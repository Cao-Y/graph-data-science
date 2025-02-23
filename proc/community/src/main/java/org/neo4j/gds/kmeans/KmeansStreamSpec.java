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
package org.neo4j.gds.kmeans;

import org.neo4j.gds.api.IdMap;
import org.neo4j.gds.executor.AlgorithmSpec;
import org.neo4j.gds.executor.ComputationResultConsumer;
import org.neo4j.gds.executor.ExecutionMode;
import org.neo4j.gds.executor.GdsCallable;
import org.neo4j.gds.executor.NewConfigFunction;

import java.util.stream.LongStream;
import java.util.stream.Stream;

import static org.neo4j.gds.kmeans.KmeansStreamProc.KMEANS_DESCRIPTION;

@GdsCallable(name = "gds.alpha.kmeans.stream", description = KMEANS_DESCRIPTION, executionMode = ExecutionMode.STREAM)
public class KmeansStreamSpec implements AlgorithmSpec<Kmeans, KmeansResult, KmeansStreamConfig, Stream<KmeansStreamProc.StreamResult>, KmeansAlgorithmFactory<KmeansStreamConfig>> {
    @Override
    public String name() {
        return "KmeansStream";
    }

    @Override
    public KmeansAlgorithmFactory<KmeansStreamConfig> algorithmFactory() {
        return new KmeansAlgorithmFactory<>();
    }

    @Override
    public NewConfigFunction<KmeansStreamConfig> newConfigFunction() {
        return (__, config) -> KmeansStreamConfig.of(config);
    }

    @Override
    public ComputationResultConsumer<Kmeans, KmeansResult, KmeansStreamConfig, Stream<KmeansStreamProc.StreamResult>> computationResultConsumer() {

        return (computationResult, executionContext) -> {
            var result = computationResult.result().communities();
            var graph = computationResult.graph();
            return LongStream
                .range(IdMap.START_NODE_ID, graph.nodeCount())
                .mapToObj(nodeId -> new KmeansStreamProc.StreamResult(
                    graph.toOriginalNodeId(nodeId),
                    result.get(nodeId)
                ));
        };
    }

}
