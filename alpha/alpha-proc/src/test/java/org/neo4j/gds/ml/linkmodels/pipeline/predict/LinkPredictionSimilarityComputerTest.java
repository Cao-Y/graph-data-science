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
package org.neo4j.gds.ml.linkmodels.pipeline.predict;

import org.junit.jupiter.api.Test;
import org.neo4j.gds.beta.generator.PropertyProducer;
import org.neo4j.gds.beta.generator.RandomGraphGenerator;
import org.neo4j.gds.beta.generator.RelationshipDistribution;
import org.neo4j.gds.core.concurrency.ParallelUtil;
import org.neo4j.gds.core.concurrency.Pools;
import org.neo4j.gds.extension.GdlExtension;
import org.neo4j.gds.extension.GdlGraph;
import org.neo4j.gds.extension.Inject;
import org.neo4j.gds.extension.TestGraph;
import org.neo4j.gds.ml.core.functions.Weights;
import org.neo4j.gds.ml.core.tensor.Matrix;
import org.neo4j.gds.ml.linkmodels.pipeline.logisticRegression.ImmutableLinkLogisticRegressionData;
import org.neo4j.gds.ml.linkmodels.pipeline.logisticRegression.LinkLogisticRegressionPredictor;
import org.neo4j.gds.ml.linkmodels.pipeline.predict.LinkPredictionSimilarityComputer.LinkFilterFactory;
import org.neo4j.gds.ml.pipeline.linkPipeline.LinkFeatureExtractor;
import org.neo4j.gds.ml.pipeline.linkPipeline.LinkFeatureStep;
import org.neo4j.gds.ml.pipeline.linkPipeline.linkfunctions.CosineFeatureStep;
import org.neo4j.gds.ml.pipeline.linkPipeline.linkfunctions.HadamardFeatureStep;
import org.neo4j.gds.similarity.knn.NeighborFilter;

import java.util.List;
import java.util.Random;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.neo4j.gds.Orientation.UNDIRECTED;

@GdlExtension
class LinkPredictionSimilarityComputerTest {
    @GdlGraph
    private static final String GDL =
        "CREATE" +
        "  (a { prop1: 4,  prop2: [1L, 0L, 0L, 0L]})" +
        "  (b { prop1: 100,  prop2: [0L, 10L, 0L, 0L]})" +
        ", (c { prop1: 1, prop2: [2L, 1L, 0L, 0L]})"+
        ", (a)-->(a)" +
        ", (a)-->(b)"+
        ", (c)-->(a)";


    @Inject
    public TestGraph graph;

    @Test
    void validateComputeSimilarity() {
        var linkFeatureSteps = List.<LinkFeatureStep>of(
            new CosineFeatureStep(List.of("prop2")),
            new HadamardFeatureStep(List.of("prop1"))
        );
        var linkFeatureExtractor = LinkFeatureExtractor.of(graph, linkFeatureSteps);
        var modelData = ImmutableLinkLogisticRegressionData.of(
            new Weights<>(new Matrix(
                new double[]{1, 0.0001},
                1,
                2
            )),
            Weights.ofScalar(0)
        );
        var lpSimComputer = new LinkPredictionSimilarityComputer(
            linkFeatureExtractor,
            new LinkLogisticRegressionPredictor(modelData),
            graph
        );
        assertThat(lpSimComputer.similarity(graph.toMappedNodeId("a"), graph.toMappedNodeId("b"))).isEqualTo(
            0.5099986668799655);
        assertThat(lpSimComputer.similarity(graph.toMappedNodeId("a"), graph.toMappedNodeId("c"))).isEqualTo(
            0.7098853299317623);
    }

    @Test
    void filterExistingRelationships() {
        var linkFeatureSteps = List.<LinkFeatureStep>of();
        var linkFeatureExtractor = LinkFeatureExtractor.of(graph, linkFeatureSteps);
        var modelData = ImmutableLinkLogisticRegressionData.of(
            Weights.ofMatrix(1, 2),
            Weights.ofScalar(0)
        );
        var lpSimComputer = new LinkPredictionSimilarityComputer(
            linkFeatureExtractor,
            new LinkLogisticRegressionPredictor(modelData),
            graph
        );

        NeighborFilter filter = new LinkFilterFactory(graph).create();

        // The node filter does not support self-loops as a node is always similar to itself so a-->a should be false.
        assertThat(filter.excludeNodePair(graph.toMappedNodeId("a"), graph.toMappedNodeId("a"))).isEqualTo(true);
        assertThat(filter.excludeNodePair(graph.toMappedNodeId("a"), graph.toMappedNodeId("b"))).isEqualTo(true);
        assertThat(filter.excludeNodePair(graph.toMappedNodeId("b"), graph.toMappedNodeId("a"))).isEqualTo(false);
        assertThat(filter.excludeNodePair(graph.toMappedNodeId("b"), graph.toMappedNodeId("b"))).isEqualTo(true);
        assertThat(filter.excludeNodePair(graph.toMappedNodeId("c"), graph.toMappedNodeId("a"))).isEqualTo(true);
        assertThat(filter.excludeNodePair(graph.toMappedNodeId("c"), graph.toMappedNodeId("c"))).isEqualTo(true);
        assertThat(filter.excludeNodePair(graph.toMappedNodeId("b"), graph.toMappedNodeId("c"))).isEqualTo(false);
        assertThat(filter.excludeNodePair(graph.toMappedNodeId("a"), graph.toMappedNodeId("c"))).isEqualTo(false);
        assertThat(filter.excludeNodePair(graph.toMappedNodeId("c"), graph.toMappedNodeId("b"))).isEqualTo(false);
    }

    @Test
    void neighbourEstimates() {
        var linkFeatureSteps = List.<LinkFeatureStep>of();
        var linkFeatureExtractor = LinkFeatureExtractor.of(graph, linkFeatureSteps);
        var modelData = ImmutableLinkLogisticRegressionData.of(
            Weights.ofMatrix(1, 2),
            Weights.ofScalar(0)
        );
        var lpSimComputer = new LinkPredictionSimilarityComputer(
            linkFeatureExtractor,
            new LinkLogisticRegressionPredictor(modelData),
            graph
        );

        NeighborFilter filter = new LinkFilterFactory(graph).create();

        assertThat(filter.lowerBoundOfPotentialNeighbours(graph.toMappedNodeId("a")))
            .isLessThanOrEqualTo(1)
            .isGreaterThanOrEqualTo(0);
        assertThat(filter.lowerBoundOfPotentialNeighbours(graph.toMappedNodeId("b")))
            .isLessThanOrEqualTo(2)
            .isGreaterThanOrEqualTo(0);
        assertThat(filter.lowerBoundOfPotentialNeighbours(graph.toMappedNodeId("c")))
            .isLessThanOrEqualTo(1)
            .isGreaterThanOrEqualTo(0);
    }

    @Test
    void shouldFailIfWeCouldFigureOutHowToWriteTheTestCorrectly() {
        var nodeCount = 1500;
        var graph = RandomGraphGenerator.builder()
            .nodeCount(nodeCount)
            .nodePropertyProducer(PropertyProducer.randomDouble("prop", 0.0, 1.0))
            .averageDegree(1)
            .relationshipDistribution(RelationshipDistribution.UNIFORM)
            .orientation(UNDIRECTED)
            .seed(43L)
            .build()
            .generate();

        NeighborFilter filter = new LinkFilterFactory(graph).create();

        Random random = new Random();

        Runnable r1 = runnable(nodeCount, 0, nodeCount / 2, random, filter);
        Runnable r2 = runnable(nodeCount, nodeCount / 2 + 1, nodeCount, random, filter);

        var tasks = List.of(r1, r2);

        ParallelUtil.run(tasks, Pools.DEFAULT);
    }

    private Runnable runnable(long nodeCount, long startNodeId, long endNodeId, Random random, NeighborFilter filter) {

        return () -> {
            LongStream.range(startNodeId, endNodeId)
                .forEach(sourceNode ->
                    LongStream.range(startNodeId, endNodeId)
                        .forEach(__ -> {
                                var targetNode = Math.abs(random.nextLong()) % nodeCount;
                                filter.excludeNodePair(sourceNode, targetNode);
                            }
                        )
                );
        };

    }
}
