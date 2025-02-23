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
package org.neo4j.gds.similarity.filteredknn;

import org.junit.jupiter.api.Test;
import org.neo4j.gds.similarity.SimilarityResult;

import static org.assertj.core.api.Assertions.assertThat;

class TargetNodeFilterTest {
    @Test
    void shouldPrioritiseTargetNodes() {
        TargetNodeFilter consumer = new TargetNodeFilter(l -> true, 3);

        consumer.offer(23, 3.14);
        consumer.offer(42, 1.61);
        consumer.offer(87, 2.71);

        assertThat(consumer.asSimilarityStream(117)).containsExactly(
            new SimilarityResult(117, 23, 3.14),
            new SimilarityResult(117, 87, 2.71),
            new SimilarityResult(117, 42, 1.61)
        );
    }

    @Test
    void shouldOnlyKeepTopK() {
        TargetNodeFilter consumer = new TargetNodeFilter(l -> true, 2);

        consumer.offer(23, 3.14);
        consumer.offer(42, 1.61);
        consumer.offer(87, 2.71);

        assertThat(consumer.asSimilarityStream(117)).containsExactly(
            new SimilarityResult(117, 23, 3.14),
            new SimilarityResult(117, 87, 2.71)
        );
    }

    @Test
    void shouldOnlyIncludeTargetNodes() {
        TargetNodeFilter consumer = new TargetNodeFilter(l -> false, 3);

        consumer.offer(23, 3.14);
        consumer.offer(42, 1.61);
        consumer.offer(87, 2.71);

        assertThat(consumer.asSimilarityStream(117)).isEmpty();
    }

    @Test
    void shouldIgnoreExactDuplicates() {
        TargetNodeFilter consumer = new TargetNodeFilter(l -> true, 4);

        consumer.offer(23, 3.14);
        consumer.offer(42, 1.61);
        consumer.offer(87, 2.71);
        consumer.offer(42, 1.61);

        assertThat(consumer.asSimilarityStream(117)).containsExactly(
            new SimilarityResult(117, 23, 3.14),
            new SimilarityResult(117, 87, 2.71),
            new SimilarityResult(117, 42, 1.61)
        );
    }

    /**
     * This is documenting a fact rather than illustrating something desirable.
     */
    @Test
    void shouldAllowDuplicateElementsWithNewPriorities() {
        TargetNodeFilter consumer = new TargetNodeFilter(l -> true, 4);

        consumer.offer(23, 3.14);
        consumer.offer(42, 1.61);
        consumer.offer(87, 2.71);
        consumer.offer(42, 1.41);

        assertThat(consumer.asSimilarityStream(117)).containsExactly(
            new SimilarityResult(117, 23, 3.14),
            new SimilarityResult(117, 87, 2.71),
            new SimilarityResult(117, 42, 1.61),
            new SimilarityResult(117, 42, 1.41)
        );
    }
}
