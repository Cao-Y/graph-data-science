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
package org.neo4j.gds.ml.pipeline.nodePipeline;

import org.neo4j.gds.api.Graph;
import org.neo4j.gds.config.ToMapConvertible;
import org.neo4j.gds.ml.pipeline.FeatureStep;
import org.neo4j.gds.ml.pipeline.FeatureStepUtil;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class NodeFeatureStep implements ToMapConvertible, FeatureStep {

    private final String nodeProperty;

    public static NodeFeatureStep of(String nodeProperty) {
        return new NodeFeatureStep(nodeProperty);
    }

    public NodeFeatureStep(String nodeProperty) {
        this.nodeProperty = nodeProperty;
    }

    @Override
    public List<String> inputNodeProperties() {
        return List.of(nodeProperty);
    }

    @Override
    public String name() {
        return "feature";
    }

    @Override
    public Map<String, Object> configuration() {
        return Map.of("nodeProperty", nodeProperty);
    }

    @Override
    public int featureDimension(Graph graph) {
        return FeatureStepUtil.propertyDimension(graph, nodeProperty);
    }

    @Override
    public Map<String, Object> toMap() {
        return Map.of(name(), nodeProperty);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodeFeatureStep that = (NodeFeatureStep) o;
        return Objects.equals(nodeProperty, that.nodeProperty);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeProperty);
    }
}
