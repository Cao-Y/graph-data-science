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
package org.neo4j.gds.api.properties.graph;

import org.jetbrains.annotations.Nullable;
import org.neo4j.gds.api.nodeproperties.ValueType;
import org.neo4j.values.storable.Value;
import org.neo4j.values.storable.Values;

import java.util.stream.Stream;

public interface DoubleArrayGraphPropertyValues extends GraphPropertyValues {

    @Override
    Stream<double[]> doubleArrayValues();

    @Override
    default Stream<Object> objects() {
        return doubleArrayValues().map(a -> a);
    }

    @Override
    default Stream<Value> values() {
        return doubleArrayValues().map(value -> value == null ? null : Values.doubleArray(value));
    }

    @Override
    @Nullable
    default Stream<float[]> floatArrayValues() {
        return doubleArrayValues().map(doubleArray -> {
        if (doubleArray == null) {
            return null;
        } else {
            float[] floatArray = new float[doubleArray.length];
            for (int i = 0; i < doubleArray.length; i++) {
                floatArray[i] = (float) doubleArray[i];
            }
            return floatArray;
        }
        });
    }

    @Override
    default ValueType valueType() {
        return ValueType.DOUBLE_ARRAY;
    }
}
