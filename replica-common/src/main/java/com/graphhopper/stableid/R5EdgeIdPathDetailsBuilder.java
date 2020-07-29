package com.graphhopper.stableid;

import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.querygraph.VirtualEdgeIteratorState;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.details.AbstractPathDetailsBuilder;

public class R5EdgeIdPathDetailsBuilder extends AbstractPathDetailsBuilder {
    private final StableIdEncodedValues originalDirectionFlagEncoder;
    private String edgeId;

    public R5EdgeIdPathDetailsBuilder(EncodedValueLookup originalDirectionFlagEncoder) {
        super("r5_edge_id");
        this.originalDirectionFlagEncoder = StableIdEncodedValues.fromEncodingManager((EncodingManager) originalDirectionFlagEncoder);
        edgeId = "";
    }

    @Override
    public boolean isEdgeDifferentToLastEdge(EdgeIteratorState edge) {
        String newEdgeId = getR5EdgeId(edge);
        if (newEdgeId.equals(edgeId)) {
            return false;
        }
        edgeId = newEdgeId;
        return true;
    }

    private String getR5EdgeId(EdgeIteratorState edge) {
        if (edge instanceof VirtualEdgeIteratorState) {
            return String.valueOf(GHUtility.getEdgeFromEdgeKey(((VirtualEdgeIteratorState) edge).getOriginalEdgeKey()));
        } else {
            boolean reverse = edge.get(EdgeIteratorState.REVERSE_STATE);
            return originalDirectionFlagEncoder.getStableId(reverse, edge);
        }
    }

    @Override
    public Object getCurrentValue() {
        return this.edgeId;
    }
}
