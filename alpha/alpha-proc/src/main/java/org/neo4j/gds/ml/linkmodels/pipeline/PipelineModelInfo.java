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
package org.neo4j.gds.ml.linkmodels.pipeline;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.neo4j.gds.ml.linkmodels.pipeline.linkFeatures.LinkFeatureStep;
import org.neo4j.graphalgo.core.model.Model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PipelineModelInfo implements Model.Mappable {
    private final List<NodePropertyStep> nodePropertySteps;
    private final List<LinkFeatureStep> featureSteps;
    private Optional<Map<String, Object>> splitConfig;
    // Either list of specific parameter combinations (in the future also a map with value ranges for different parameters will be allowed)
    private Optional<List<Map<String, Object>>> parameterSpace;

    public static PipelineModelInfo create() {
        return new PipelineModelInfo(null, null);
    }

    private PipelineModelInfo(
        @Nullable Map<String, Object> splitConfig,
        @Nullable List<Map<String, Object>> parameterSpace
    ) {
        this.nodePropertySteps = new ArrayList<>();
        this.featureSteps = new ArrayList<>();
        this.splitConfig = Optional.ofNullable(splitConfig);
        this.parameterSpace = Optional.ofNullable(parameterSpace);
    }

    @Override
    public Map<String, Object> toMap() {
        return Map.of(
            "featurePipeline", Map.of(
                "nodePropertySteps", nodePropertySteps,
                "featureSteps", featureSteps
            ),
            "splitConfig", splitConfig,
            "parameterSpace", parameterSpace
        );
    }

    List<NodePropertyStep> nodePropertySteps() {
        return nodePropertySteps;
    }

    void addNodePropertyStep(NodePropertyStep step) {
        nodePropertySteps.add(step);
    }

    List<LinkFeatureStep> featureSteps() {
        return featureSteps;
    }

    void addNFeatureStep(LinkFeatureStep step) {
        featureSteps.add(step);
    }

    Optional<Map<String, Object>> splitConfig() {
        return splitConfig;
    }

    void setSplitConfig(@NotNull Map<String, Object> splitConfig) {
        this.splitConfig = Optional.of(splitConfig);
    }

    Optional<List<Map<String, Object>>> parameterSpace() {
        return parameterSpace;
    }

    void setParameterSpace(@NotNull List<Map<String, Object>> parameterList) {
        this.parameterSpace = Optional.of(parameterList);
    }
}
