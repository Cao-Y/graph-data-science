/*
 * Copyright (c) 2017-2020 "Neo4j,"
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
package org.neo4j.graphalgo.results;

import org.HdrHistogram.DoubleHistogram;

public class ApproxSimilaritySummaryResult {

    public final long nodes;
    public final long similarityPairs;
    public final long computations;
    public final String writeRelationshipType;
    public final String writeProperty;
    public final double min;
    public final double max;
    public final double mean;
    public final double stdDev;
    public final double p25;
    public final double p50;
    public final double p75;
    public final double p90;
    public final double p95;
    public final double p99;
    public final double p999;
    public final double p100;
    public final long iterations;
    public final double scanRate;

    public ApproxSimilaritySummaryResult(
            long nodes,
            long similarityPairs,
            long computations,
            String writeRelationshipType,
            String writeProperty,
            double min,
            double max,
            double mean,
            double stdDev,
            double p25,
            double p50,
            double p75,
            double p90,
            double p95,
            double p99,
            double p999,
            double p100,
            long iterations) {
        this.nodes = nodes;
        this.similarityPairs = similarityPairs;
        this.computations = computations;
        this.writeRelationshipType = writeRelationshipType;
        this.writeProperty = writeProperty;
        this.min = min;
        this.max = max;
        this.mean = mean;
        this.stdDev = stdDev;
        this.p25 = p25;
        this.p50 = p50;
        this.p75 = p75;
        this.p90 = p90;
        this.p95 = p95;
        this.p99 = p99;
        this.p999 = p999;
        this.p100 = p100;
        this.iterations = iterations;
        this.scanRate = computeScanRate(nodes, computations);
    }

    private double computeScanRate(final long nodes, final long computations) {
        long maximumComputations = (nodes * (nodes - 1)) / 2;
        return computations * 1.0 / maximumComputations;
    }

    public static ApproxSimilaritySummaryResult from(
            long nodes,
            long similarityPairs,
            long computations,
            String writeRelationshipType,
            String writeProperty,
            long iterations,
            DoubleHistogram histogram) {
        return new ApproxSimilaritySummaryResult(
                nodes,
                similarityPairs,
                computations,
                writeRelationshipType,
                writeProperty,
                histogram.getMinValue(),
                histogram.getMaxValue(),
                histogram.getMean(),
                histogram.getStdDeviation(),
                histogram.getValueAtPercentile(25D),
                histogram.getValueAtPercentile(50D),
                histogram.getValueAtPercentile(75D),
                histogram.getValueAtPercentile(90D),
                histogram.getValueAtPercentile(95D),
                histogram.getValueAtPercentile(99D),
                histogram.getValueAtPercentile(99.9D),
                histogram.getValueAtPercentile(100D),
                iterations
        );
    }
}
