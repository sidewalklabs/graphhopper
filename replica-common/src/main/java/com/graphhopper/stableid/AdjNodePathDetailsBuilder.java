package com.graphhopper.stableid;

import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.details.AbstractPathDetailsBuilder;

public class AdjNodePathDetailsBuilder extends AbstractPathDetailsBuilder {

    private int edgeId = -1;
    private int adjNode = -1;

    public AdjNodePathDetailsBuilder() {
        super("adj_node");
    }

    @Override
    public boolean isEdgeDifferentToLastEdge(EdgeIteratorState edge) {
        if (edge.getEdge() != edgeId) {
            edgeId = edge.getEdge();
            adjNode = edge.getAdjNode();
            return true;
        }
        return false;
    }

    @Override
    public Object getCurrentValue() {
        return this.adjNode;
    }
}
