/*
 * Copyright (c) 2017-2019 "Neo4j,"
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
package org.neo4j.graphalgo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.neo4j.graphalgo.core.utils.ProgressTimer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;


/**
 * Test for <a href="https://github.com/neo4j-contrib/neo4j-graph-algorithms/issues/282">Issue 282</a>
 *
 *    (B)--.    (F)
 *    /     \  /   \
 *  (A)-(C)-(E)   (H)
 *    \     /  \  /
 *    (D)--´   (G)
 */
@ExtendWith(MockitoExtension.class)
class BetweennessCentralityProcTest_282 extends ProcTestBase {

    private static final double[] EXPECTED = {
            0.0,
            1.33333,
            1.33333,
            1.33333,
            12.0,
            2.5,
            2.5,
            0
    };

    @BeforeEach
    void setupGraph() throws Exception {

        db = TestDatabaseCreator.createTestDatabase();

        registerProcedures(BetweennessCentralityProc.class);

        final String importQuery =
                "CREATE (a:Node{id:'A'}),\n" +
                        "(b:Node{id:'B'}),\n" +
                        "(c:Node{id:'C'}),\n" +
                        "(d:Node{id:'D'}),\n" +
                        "(e:Node{id:'E'}),\n" +
                        "(f:Node{id:'F'}),\n" +
                        "(g:Node{id:'G'}),\n" +
                        "(h:Node{id:'H'})\n" +
                "CREATE (a)-[:EDGE]->(b),\n" +
                        "(a)-[:EDGE]->(c),\n" +
                        "(a)-[:EDGE]->(d),\n" +
                        "(b)-[:EDGE]->(e),\n" +
                        "(c)-[:EDGE]->(e),\n" +
                        "(d)-[:EDGE]->(e),\n" +
                        "(e)-[:EDGE]->(f),\n" +
                        "(e)-[:EDGE]->(g),\n" +
                        "(f)-[:EDGE]->(h),\n" +
                        "(g)-[:EDGE]->(h)";

        try (ProgressTimer timer = ProgressTimer.start(l -> System.out.printf("Setup took %d ms%n", l))) {
            runQuery(importQuery);
        }

    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    /**
     * test org.neo4j.graphalgo.impl.BetweennessCentrality
     *
     * @throws Exception
     */
    @Test
    void testBCWriteBack() throws Exception {

        final String evalQuery = "CALL algo.betweenness('Node', 'EDGE', {write:true, stats:true, writeProperty:'centrality'})\n" +
                "YIELD nodes, minCentrality, maxCentrality, sumCentrality";

        runQuery(evalQuery, row -> {
            final long nodes = row.getNumber("nodes").longValue();
            final double minCentrality = row.getNumber("minCentrality").doubleValue();
            final double maxCentrality = row.getNumber("maxCentrality").doubleValue();
            final double sumCentrality = row.getNumber("sumCentrality").doubleValue();

            System.out.println("nodes = " + nodes);
            System.out.println("minCentrality = " + minCentrality);
            System.out.println("maxCentrality = " + maxCentrality);
            System.out.println("sumCentrality = " + sumCentrality);
        });

        final String checkQuery = "MATCH (n) WHERE exists(n.centrality) RETURN id(n) as id, n.centrality as c";
        final double[] result = new double[EXPECTED.length];
        runQuery(checkQuery, row -> {
            final int id = row.getNumber("id").intValue();
            final double c = row.getNumber("c").doubleValue();
            result[id] = c;

            System.out.printf("id: %2d centrality: %f%n", id, c);
        });

        assertArrayEquals(EXPECTED, result, 0.1);
    }

    /**
     * test org.neo4j.graphalgo.impl.BetweennessCentrality
     *
     * @throws Exception
     */
    @Test
    void testBCWriteBackParallel() throws Exception {

        final String evalQuery = "CALL algo.betweenness('Node', 'EDGE', {write:true, stats:true, writeProperty:'centrality', concurrency:4})\n" +
                "YIELD nodes, minCentrality, maxCentrality, sumCentrality";

        runQuery(evalQuery, row -> {
            final long nodes = row.getNumber("nodes").longValue();
            final double minCentrality = row.getNumber("minCentrality").doubleValue();
            final double maxCentrality = row.getNumber("maxCentrality").doubleValue();
            final double sumCentrality = row.getNumber("sumCentrality").doubleValue();

            System.out.println("nodes = " + nodes);
            System.out.println("minCentrality = " + minCentrality);
            System.out.println("maxCentrality = " + maxCentrality);
            System.out.println("sumCentrality = " + sumCentrality);
        });

        final String checkQuery = "MATCH (n) WHERE exists(n.centrality) RETURN id(n) as id, n.centrality as c";
        final double[] result = new double[EXPECTED.length];
        runQuery(checkQuery, row -> {
            final int id = row.getNumber("id").intValue();
            final double c = row.getNumber("c").doubleValue();
            result[id] = c;
        });

        assertArrayEquals(EXPECTED, result, 0.1);
    }
}
