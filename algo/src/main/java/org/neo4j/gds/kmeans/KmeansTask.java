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

import org.neo4j.gds.api.properties.nodes.NodePropertyValues;
import org.neo4j.gds.core.utils.paged.HugeIntArray;
import org.neo4j.gds.core.utils.partition.Partition;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;

import java.util.Arrays;

public abstract class KmeansTask implements Runnable {
    ClusterManager clusterManager;
    final ProgressTracker progressTracker;
    final Partition partition;
    final NodePropertyValues nodePropertyValues;
    final HugeIntArray communities;
    final long[] communitySizes;
    final int k;
    final int dimensions;
    long swaps;

    long getNumAssignedAtCenter(int ith) {
        return communitySizes[ith];
    }

    long getSwaps() {
        return swaps;
    }

    abstract void reset();

    abstract void updateAfterAssignment(long nodeId, int community);

    KmeansTask(
        ClusterManager clusterManager,
        NodePropertyValues nodePropertyValues,
        HugeIntArray communities,
        int k,
        int dimensions,
        Partition partition,
        ProgressTracker progressTracker
    ) {
        this.clusterManager = clusterManager;
        this.nodePropertyValues = nodePropertyValues;
        this.communities = communities;
        this.k = k;
        this.dimensions = dimensions;
        this.partition = partition;
        this.progressTracker = progressTracker;
        this.communitySizes = new long[k];
    }

    static KmeansTask createTask(
        ClusterManager clusterManager,
        NodePropertyValues nodePropertyValues,
        HugeIntArray communities,
        int k,
        int dimensions,
        Partition partition,
        ProgressTracker progressTracker
    ) {
        if (clusterManager instanceof DoubleClusterManager) {
            return new DoubleKmeansTask(
                clusterManager,
                nodePropertyValues,
                communities,
                k,
                dimensions,
                partition,
                progressTracker
            );
        }
        return new FloatKmeansTask(
            clusterManager,
            nodePropertyValues,
            communities,
            k,
            dimensions,
            partition,
            progressTracker
        );
    }

    @Override
    public void run() {
        var startNode = partition.startNode();
        long endNode = startNode + partition.nodeCount();
        swaps = 0;

        reset();

        for (long nodeId = startNode; nodeId < endNode; nodeId++) {
            int closestCommunity = clusterManager.findClosestCenter(nodeId);
            communitySizes[closestCommunity]++;
            int previousCommunity = communities.get(nodeId);
            if (closestCommunity != previousCommunity) {
                swaps++;
            }
            communities.set(nodeId, closestCommunity);
            //Note for potential improvement : This is potentially costly when clusters have somewhat stabilized.
            //Because we keep adding the same nodes to the same clusters. Perhaps instead of making the sum 0
            //we keep as is and do a subtraction when a node changes its cluster.
            //On that note,  maybe we can skip stable communities (i.e., communities that did not change between one iteration to another)
            // or avoid calculating their distance from other nodes etc...
            updateAfterAssignment(nodeId, closestCommunity);
        }
    }
}

final class DoubleKmeansTask extends KmeansTask {

    private final double[][] communityCoordinateSums;

    DoubleKmeansTask(
        ClusterManager clusterManager,
        NodePropertyValues nodePropertyValues,
        HugeIntArray communities,
        int k,
        int dimensions,
        Partition partition,
        ProgressTracker progressTracker
    ) {
        super(clusterManager, nodePropertyValues, communities, k, dimensions, partition, progressTracker);
        this.communityCoordinateSums = new double[k][dimensions];

    }

    double[] getCenterContribution(int ith) {
        return communityCoordinateSums[ith];
    }

    @Override
    void reset() {
        for (int community = 0; community < k; ++community) {
            communitySizes[community] = 0;
            Arrays.fill(communityCoordinateSums[community], 0.0d);
        }
    }

    @Override
    void updateAfterAssignment(long nodeId, int community) {
        var property = nodePropertyValues.doubleArrayValue(nodeId);
        communities.set(nodeId, community);
        for (int j = 0; j < dimensions; ++j) {
            communityCoordinateSums[community][j] += property[j];
        }
    }

}

final class FloatKmeansTask extends KmeansTask {

    private final float[][] communityCoordinateSums;

    FloatKmeansTask(
        ClusterManager clusterManager,
        NodePropertyValues nodePropertyValues,
        HugeIntArray communities,
        int k,
        int dimensions,
        Partition partition,
        ProgressTracker progressTracker
    ) {
        super(clusterManager, nodePropertyValues, communities, k, dimensions, partition, progressTracker);
        this.communityCoordinateSums = new float[k][dimensions];
    }

    float[] getCenterContribution(int ith) {
        return communityCoordinateSums[ith];
    }

    @Override
    void reset() {
        for (int community = 0; community < k; ++community) {
            communitySizes[community] = 0;
            Arrays.fill(communityCoordinateSums[community], 0.0f);
        }
    }

    @Override
    void updateAfterAssignment(long nodeId, int community) {
        var property = nodePropertyValues.floatArrayValue(nodeId);
        communities.set(nodeId, community);
        for (int j = 0; j < dimensions; ++j) {
            communityCoordinateSums[community][j] += property[j];
        }
    }

}

