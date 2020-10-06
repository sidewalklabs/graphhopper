package com.graphhopper.replica;

import com.graphhopper.GraphHopper;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.stableid.StableIdEncodedValues;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.NodeAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StableEdgeIdManager {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final GraphHopper graphHopper;

    public StableEdgeIdManager(GraphHopper graphHopper) {
        this.graphHopper = graphHopper;
    }

    public void setStableEdgeIds() {
        GraphHopperStorage graphHopperStorage = graphHopper.getGraphHopperStorage();
        AllEdgesIterator edgesIterator = graphHopperStorage.getAllEdges();
        NodeAccess nodes = graphHopperStorage.getNodeAccess();
        EncodingManager encodingManager = graphHopper.getEncodingManager();
        StableIdEncodedValues stableIdEncodedValues = StableIdEncodedValues.fromEncodingManager(encodingManager);

        // Set both forward and reverse stable edge IDs for each edge
        int assignedIdCount = 0;
        while (edgesIterator.next()) {
            // Ignore setting stable IDs for transit edges, which have a distance of 0
            if (edgesIterator.getDistance() != 0) {
                stableIdEncodedValues.setStableId(true, edgesIterator, nodes);
                stableIdEncodedValues.setStableId(false, edgesIterator, nodes);
                assignedIdCount++;
            }
        }
        graphHopperStorage.flush();
        logger.info("Total number of bidirectional edges assigned with stable edge IDs: " + assignedIdCount);
    }
}
