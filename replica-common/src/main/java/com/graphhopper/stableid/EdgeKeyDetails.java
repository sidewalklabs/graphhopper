package com.graphhopper.stableid;

import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.details.AbstractPathDetailsBuilder;

public class EdgeKeyDetails extends AbstractPathDetailsBuilder {
    private int edgeKey;

    public EdgeKeyDetails() {
        super("edge_key");
        edgeKey = -1;
    }

    @Override
    public boolean isEdgeDifferentToLastEdge(EdgeIteratorState edge) {
        int newEdgeKey = EdgeKeys.getEdgeKey(edge);
        if (newEdgeKey != edgeKey) {
            edgeKey = newEdgeKey;
            return true;
        }
        return false;
    }

    @Override
    public Object getCurrentValue() {
        return this.edgeKey;
    }
}
