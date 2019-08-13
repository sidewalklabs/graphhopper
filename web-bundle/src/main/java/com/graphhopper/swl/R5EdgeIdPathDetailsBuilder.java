/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.graphhopper.swl;

import com.graphhopper.routing.VirtualEdgeIteratorState;
import com.graphhopper.routing.profiles.SimpleIntEncodedValue;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.details.AbstractPathDetailsBuilder;

import javax.xml.bind.DatatypeConverter;

public class R5EdgeIdPathDetailsBuilder extends AbstractPathDetailsBuilder {
    private final CustomCarFlagEncoder originalDirectionFlagEncoder;
    private String edgeId;

    public R5EdgeIdPathDetailsBuilder(CustomCarFlagEncoder originalDirectionFlagEncoder) {
        super("r5_edge_id");
        this.originalDirectionFlagEncoder = originalDirectionFlagEncoder;
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
