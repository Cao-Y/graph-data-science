/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.gds.ml.pipeline.linkPipeline.train;

import org.jetbrains.annotations.NotNull;
import org.neo4j.gds.Algorithm;
import org.neo4j.gds.RelationshipType;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.core.model.Model;
import org.neo4j.gds.core.utils.mem.MemoryEstimation;
import org.neo4j.gds.core.utils.mem.MemoryEstimations;
import org.neo4j.gds.core.utils.mem.MemoryRange;
import org.neo4j.gds.core.utils.paged.HugeLongArray;
import org.neo4j.gds.core.utils.paged.ReadOnlyHugeLongArray;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.core.utils.progress.tasks.Task;
import org.neo4j.gds.core.utils.progress.tasks.Tasks;
import org.neo4j.gds.ml.core.ReadOnlyHugeLongIdentityArray;
import org.neo4j.gds.ml.core.batch.BatchQueue;
import org.neo4j.gds.ml.core.batch.HugeBatchQueue;
import org.neo4j.gds.ml.core.subgraph.LocalIdMap;
import org.neo4j.gds.ml.metrics.BestMetricData;
import org.neo4j.gds.ml.metrics.LinkMetric;
import org.neo4j.gds.ml.metrics.Metric;
import org.neo4j.gds.ml.metrics.ModelStatsBuilder;
import org.neo4j.gds.ml.metrics.SignedProbabilities;
import org.neo4j.gds.ml.metrics.StatsMap;
import org.neo4j.gds.ml.models.Classifier;
import org.neo4j.gds.ml.models.Trainer;
import org.neo4j.gds.ml.models.TrainerConfig;
import org.neo4j.gds.ml.models.TrainerFactory;
import org.neo4j.gds.ml.models.automl.RandomSearch;
import org.neo4j.gds.ml.models.automl.TunableTrainerConfig;
import org.neo4j.gds.ml.pipeline.TrainingStatistics;
import org.neo4j.gds.ml.pipeline.linkPipeline.LinkPredictionModelInfo;
import org.neo4j.gds.ml.pipeline.linkPipeline.LinkPredictionPredictPipeline;
import org.neo4j.gds.ml.pipeline.linkPipeline.LinkPredictionSplitConfig;
import org.neo4j.gds.ml.pipeline.linkPipeline.LinkPredictionTrainingPipeline;
import org.neo4j.gds.ml.splitting.EdgeSplitter;
import org.neo4j.gds.ml.splitting.StratifiedKFoldSplitter;
import org.neo4j.gds.ml.splitting.TrainingExamplesSplit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.neo4j.gds.core.utils.mem.MemoryEstimations.maxEstimation;
import static org.neo4j.gds.ml.pipeline.linkPipeline.train.LinkFeaturesAndLabelsExtractor.extractFeaturesAndLabels;

public class LinkPredictionTrain extends Algorithm<LinkPredictionTrainResult> {

    public static final String MODEL_TYPE = "LinkPrediction";

    private final Graph trainGraph;
    private final Graph validationGraph;
    private final LinkPredictionTrainingPipeline pipeline;
    private final LinkPredictionTrainConfig config;
    private final LocalIdMap classIdMap;

    public static LocalIdMap makeClassIdMap() {
        var idMap = new LocalIdMap();
        idMap.toMapped((long) EdgeSplitter.NEGATIVE);
        idMap.toMapped((long) EdgeSplitter.POSITIVE);
        return idMap;
    }

    public LinkPredictionTrain(
        Graph trainGraph,
        Graph validationGraph,
        LinkPredictionTrainingPipeline pipeline,
        LinkPredictionTrainConfig config,
        ProgressTracker progressTracker
    ) {
        super(progressTracker);
        this.trainGraph = trainGraph;
        this.validationGraph = validationGraph;
        this.pipeline = pipeline;
        this.config = config;
        this.classIdMap = makeClassIdMap();
    }

    public static Task progressTask() {
        return Tasks.task(
            LinkPredictionTrain.class.getSimpleName(),
            Tasks.leaf("extract train features"),
            Tasks.leaf("select model"),
            Trainer.progressTask("train best model"),
            Tasks.leaf("compute train metrics"),
            Tasks.task(
                "evaluate on test data",
                Tasks.leaf("extract test features"),
                Tasks.leaf("compute test metrics")
            )
        );
    }

    @Override
    public LinkPredictionTrainResult compute() {
        progressTracker.beginSubTask();

        progressTracker.beginSubTask("extract train features");
        var trainData = extractFeaturesAndLabels(
            trainGraph,
            pipeline.featureSteps(),
            config.concurrency(),
            progressTracker
        );
        var trainRelationshipIds = new ReadOnlyHugeLongIdentityArray(trainData.size());
        progressTracker.endSubTask("extract train features");

        progressTracker.beginSubTask("select model");

        var trainingStatistics = new TrainingStatistics(new ArrayList<>(config.metrics()));

        modelSelect(trainData, trainRelationshipIds, trainingStatistics);
        progressTracker.endSubTask("select model");

        // train best model on the entire training graph
        progressTracker.beginSubTask("train best model");
        var classifier = trainModel(
            trainData,
            trainRelationshipIds,
            trainingStatistics.bestParameters(),
            progressTracker
        );
        progressTracker.endSubTask("train best model");

        // evaluate the best model on the training and test graphs
        progressTracker.beginSubTask("compute train metrics");
        var outerTrainMetrics = computeTrainMetric(trainData, classifier, trainRelationshipIds, progressTracker);
        progressTracker.endSubTask("compute train metrics");

        progressTracker.beginSubTask("evaluate on test data");
        computeTestMetric(classifier, trainingStatistics);
        progressTracker.endSubTask("evaluate on test data");

        var model = createModel(
            trainingStatistics.bestParameters(),
            classifier.data(),
            trainingStatistics.metricsForWinningModel(outerTrainMetrics)
        );

        progressTracker.endSubTask();

        return LinkPredictionTrainResult.of(model, trainingStatistics);
    }

    @NotNull
    private Classifier trainModel(
        FeaturesAndLabels featureAndLabels,
        ReadOnlyHugeLongArray trainSet,
        TrainerConfig trainerConfig,
        ProgressTracker customProgressTracker
    ) {
        return TrainerFactory.create(
            trainerConfig,
            classIdMap,
            terminationFlag,
            customProgressTracker,
            config.concurrency(),
            config.randomSeed(),
            true
        ).train(featureAndLabels.features(), featureAndLabels.labels(), trainSet);
    }

    private void modelSelect(
        FeaturesAndLabels trainData,
        ReadOnlyHugeLongArray trainRelationshipIds,
        TrainingStatistics trainingStatistics
    ) {
        var validationSplits = trainValidationSplits(trainRelationshipIds, trainData.labels());

        progressTracker.setVolume(pipeline.numberOfModelSelectionTrials());

        var hyperParameterOptimizer = new RandomSearch(
            pipeline.trainingParameterSpace(),
            pipeline.numberOfModelSelectionTrials(),
            config.randomSeed()
        );

        while (hyperParameterOptimizer.hasNext()) {
            var modelParams = hyperParameterOptimizer.next();
            var trainStatsBuilder = new ModelStatsBuilder(modelParams, pipeline.splitConfig().validationFolds());
            var validationStatsBuilder = new ModelStatsBuilder(
                modelParams,
                pipeline.splitConfig().validationFolds()
            );
            for (TrainingExamplesSplit relSplit : validationSplits) {
                // train each model candidate on the train sets
                var trainSet = relSplit.trainSet();
                var validationSet = relSplit.testSet();
                // the below calls intentionally suppress progress logging of individual models
                var classifier = trainModel(
                    trainData,
                    ReadOnlyHugeLongArray.of(trainSet),
                    modelParams,
                    ProgressTracker.NULL_TRACKER
                );

                // evaluate each model candidate on the train and validation sets
                computeTrainMetric(
                    trainData,
                    classifier,
                    ReadOnlyHugeLongArray.of(trainSet),
                    ProgressTracker.NULL_TRACKER
                )
                    .forEach(trainStatsBuilder::update);
                computeTrainMetric(
                    trainData,
                    classifier,
                    ReadOnlyHugeLongArray.of(validationSet),
                    ProgressTracker.NULL_TRACKER
                )
                    .forEach(validationStatsBuilder::update);
            }

            // insert the candidates' metrics into trainStats and validationStats
            config.metrics().forEach(metric -> {
                trainingStatistics.addValidationStats(metric, validationStatsBuilder.build(metric));
                trainingStatistics.addTrainStats(metric, trainStatsBuilder.build(metric));
            });

            progressTracker.logProgress();
        }
    }

    private void computeTestMetric(Classifier classifier, TrainingStatistics trainingStatistics) {
        progressTracker.beginSubTask("extract test features");
        var testData = extractFeaturesAndLabels(
            validationGraph,
            pipeline.featureSteps(),
            config.concurrency(),
            progressTracker
        );
        progressTracker.endSubTask("extract test features");

        progressTracker.beginSubTask("compute test metrics");
        var signedProbabilities = SignedProbabilities.computeFromLabeledData(
            testData.features(),
            testData.labels(),
            classifier,
            new BatchQueue(testData.size()),
            config.concurrency(),
            progressTracker,
            terminationFlag
        );

        config.metrics().forEach(metric -> {
            double score = metric.compute(signedProbabilities, config.negativeClassWeight());
            trainingStatistics.addTestScore(metric, score);
        });
        progressTracker.endSubTask("compute test metrics");
    }

    private List<TrainingExamplesSplit> trainValidationSplits(
        ReadOnlyHugeLongArray trainRelationshipIds,
        HugeLongArray actualLabels
    ) {
        var splitter = new StratifiedKFoldSplitter(
            pipeline.splitConfig().validationFolds(),
            trainRelationshipIds,
            ReadOnlyHugeLongArray.of(actualLabels),
            config.randomSeed()
        );
        return splitter.splits();
    }

    private Map<LinkMetric, Double> computeTrainMetric(
        FeaturesAndLabels trainData,
        Classifier classifier,
        ReadOnlyHugeLongArray evaluationSet,
        ProgressTracker progressTracker
    ) {
        var signedProbabilities = SignedProbabilities.computeFromLabeledData(
            trainData.features(),
            trainData.labels(),
            classifier,
            new HugeBatchQueue(evaluationSet),
            config.concurrency(),
            progressTracker,
            terminationFlag
        );

        return config.metrics().stream().collect(Collectors.toMap(
            Function.identity(),
            metric -> metric.compute(signedProbabilities, config.negativeClassWeight())
        ));
    }

    private Model<Classifier.ClassifierData, LinkPredictionTrainConfig, LinkPredictionModelInfo> createModel(
        TrainerConfig bestParameters,
        Classifier.ClassifierData classifierData,
        Map<Metric, BestMetricData> winnerMetrics
    ) {
        return Model.of(
            config.username(),
            config.modelName(),
            MODEL_TYPE,
            trainGraph.schema(),
            classifierData,
            config,
            LinkPredictionModelInfo.of(
                bestParameters,
                winnerMetrics,
                LinkPredictionPredictPipeline.from(pipeline)
            )
        );
    }

    @Override
    public void release() {

    }

    public static MemoryEstimation estimate(
        LinkPredictionTrainingPipeline pipeline,
        LinkPredictionTrainConfig trainConfig
    ) {
        // For the computation of the MemoryTree, this estimation assumes the given input graph dimensions to contain
        // the expected set sizes for the test and train relationshipTypes. That is, the graph dimensions input needs to
        // have the test and train relationship types as well as their relationship counts.

        var splitConfig = pipeline.splitConfig();

        var builder = MemoryEstimations.builder(LinkPredictionTrain.class);

        var fudgedLinkFeatureDim = MemoryRange.of(10, 500);

        int numberOfMetrics = trainConfig.metrics().size();
        return builder
            // After the training, the training features and labels are no longer accessed.
            // As the lifetimes of training and test data is non-overlapping, we assume the max is sufficient.
            .max("Features and labels", List.of(
                LinkFeaturesAndLabelsExtractor.estimate(
                    fudgedLinkFeatureDim,
                    relCounts -> relCounts.get(RelationshipType.of(splitConfig.trainRelationshipType())),
                    "Train"
                ),
                LinkFeaturesAndLabelsExtractor.estimate(
                    fudgedLinkFeatureDim,
                    relCounts -> relCounts.get(RelationshipType.of(splitConfig.testRelationshipType())),
                    "Test"
                )
            ))
            .add(estimateTrainingAndEvaluation(pipeline, fudgedLinkFeatureDim, numberOfMetrics))
            // we do not consider the training of the best model on the outer train set as the memory estimation is at most the maximum of the model training during the model selection
            // this assumes the training is independent of the relationship set size
            .add("Outer train stats map", StatsMap.memoryEstimation(numberOfMetrics, 1, 1))
            .add("Test stats map", StatsMap.memoryEstimation(numberOfMetrics, 1, 1))
            .fixed("Best model stats", numberOfMetrics * BestMetricData.estimateMemory())
            .build();
    }

    private static MemoryEstimation estimateTrainingAndEvaluation(
        LinkPredictionTrainingPipeline pipeline,
        MemoryRange linkFeatureDimension,
        int numberOfMetrics
    ) {
        var splitConfig = pipeline.splitConfig();
        var maxEstimationOverModelCandidates = maxEstimation(
            "Max over model candidates",
            pipeline.trainingParameterSpace()
                .values()
                .stream()
                .flatMap(List::stream)
                .flatMap(TunableTrainerConfig::streamCornerCaseConfigs)
                .map(trainerConfig -> MemoryEstimations.builder("Train and evaluate model")
                    .fixed("Stats map builder train", ModelStatsBuilder.sizeInBytes(numberOfMetrics))
                    .fixed("Stats map builder validation", ModelStatsBuilder.sizeInBytes(numberOfMetrics))
                    .max("Train model and compute train metrics", List.of(
                            estimateTraining(pipeline.splitConfig(), trainerConfig, linkFeatureDimension),
                            estimateComputeTrainMetrics(pipeline.splitConfig())
                        )
                    ).build()
                ).collect(Collectors.toList())
        );

        return MemoryEstimations.builder("model selection")
            .add(
                "Cross-Validation splitting",
                StratifiedKFoldSplitter.memoryEstimation(
                    splitConfig.validationFolds(),
                    dim -> dim.relationshipCounts().get(RelationshipType.of(splitConfig.trainRelationshipType()))
                )
            )
            .add(maxEstimationOverModelCandidates)
            .add(
                "Inner train stats map",
                StatsMap.memoryEstimation(numberOfMetrics, pipeline.numberOfModelSelectionTrials(), 1)
            )
            .add(
                "Validation stats map",
                StatsMap.memoryEstimation(numberOfMetrics, pipeline.numberOfModelSelectionTrials(), 1)
            )
            .build();
    }

    private static MemoryEstimation estimateTraining(
        LinkPredictionSplitConfig splitConfig,
        TrainerConfig trainerConfig,
        MemoryRange linkFeatureDimension
    ) {
        return MemoryEstimations.setup(
            "Training", dim ->
                TrainerFactory.memoryEstimation(
                    trainerConfig,
                    unused -> dim.relationshipCounts().get(RelationshipType.of(splitConfig.trainRelationshipType())),
                    2,
                    linkFeatureDimension,
                    true
                )
        );
    }

    private static MemoryEstimation estimateComputeTrainMetrics(LinkPredictionSplitConfig splitConfig) {
        return MemoryEstimations
            .builder("Compute train metrics")
            .perGraphDimension(
                "Sorted probabilities",
                (dim, threads) -> {
                    long trainSetSize = dim
                        .relationshipCounts()
                        .get(RelationshipType.of(splitConfig.trainRelationshipType()));
                    return MemoryRange.of(SignedProbabilities.estimateMemory(trainSetSize));
                }
            ).build();
    }
}
