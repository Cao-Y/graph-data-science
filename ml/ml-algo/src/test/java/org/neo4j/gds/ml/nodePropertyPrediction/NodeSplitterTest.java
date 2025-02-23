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
package org.neo4j.gds.ml.nodePropertyPrediction;

import org.junit.jupiter.api.Test;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;

import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

class NodeSplitterTest {

    @Test
    void testSplitter() {
        int numberOfExamples = 12;
        var splitter = new NodeSplitter(
            numberOfExamples,
            id -> id % 4,
            new TreeSet<>(List.of(0L, 1L, 2L, 3L)),
            ProgressTracker.NULL_TRACKER
        );

        double testFraction = 0.25;
        int validationFolds = 3;
        var splits = splitter.split(testFraction, validationFolds, Optional.of(42L));

        assertThat(splits.allTrainingExamples().toArray())
            .hasSize(numberOfExamples)
            .containsExactlyInAnyOrder(LongStream.range(0, numberOfExamples).toArray())
            .matches(ids -> !isSorted(ids));

        assertThat(splits.outerSplit())
            .matches(outerSplits -> outerSplits.testSet().size() == numberOfExamples * testFraction)
            .matches(outerSplits -> outerSplits.trainSet().size() == numberOfExamples * (1 - testFraction));

        assertThat(splits.innerSplits()).hasSize(validationFolds);
    }

    boolean isSorted(long... elements) {
        long lastElement = -1;
        for (long element : elements) {
            if (element < lastElement) {
                return false;
            }
            lastElement = element;
        }

        return true;
    }

}
