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
package org.neo4j.gds.paths.traverse;

import org.jetbrains.annotations.Nullable;
import org.neo4j.graphdb.Path;

import java.util.List;
import java.util.Objects;

public final class DfsStreamResult {

    public final Long sourceNode;
    public final List<Long> nodeIds;
    public final Path path;

    DfsStreamResult(long sourceNode, List<Long> nodeIds, @Nullable Path path) {
        this.sourceNode = sourceNode;
        this.nodeIds = nodeIds;
        this.path = path;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DfsStreamResult that = (DfsStreamResult) o;
        return sourceNode.equals(that.sourceNode) && nodeIds.equals(that.nodeIds) && Objects.equals(
            path,
            that.path
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceNode, nodeIds, path);
    }
}
