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
package org.neo4j.gds.ml.logisticregression;

import org.neo4j.gds.modeltraining.Features;

public final class TestFeatures implements Features {

    private final double[][] features;

    public static Features singleConstant(double feature) {
        return new Features() {
            @Override
            public long size() {
                return 1;
            }

            @Override
            public double[] get(long id) {
                return new double[]{feature};
            }
        };
    }

    public TestFeatures(double[][] features) {
        this.features = features;
    }

    @Override
    public long size() {
        return features.length;
    }

    @Override
    public double[] get(long id) {
        return features[(int) id];
    }
}
