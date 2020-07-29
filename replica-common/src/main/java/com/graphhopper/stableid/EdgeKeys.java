package com.graphhopper.stableid;

import com.graphhopper.routing.querygraph.VirtualEdgeIteratorState;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;

public class EdgeKeys {

    public static int getEdgeKey(EdgeIteratorState edge) {
        final int edgeIndex;
        if (edge instanceof VirtualEdgeIteratorState) {
            edgeIndex = GHUtility.getEdgeFromEdgeKey(((VirtualEdgeIteratorState) edge).getOriginalEdgeKey());
        } else {
            edgeIndex = edge.getEdge();
        }
        return edgeIndex * 2 + (!edge.get(EdgeIteratorState.REVERSE_STATE) ? 0 : 1);
    }

}
