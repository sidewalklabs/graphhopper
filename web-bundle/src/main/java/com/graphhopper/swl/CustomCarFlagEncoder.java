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

import com.graphhopper.routing.profiles.EncodedValue;
import com.graphhopper.routing.profiles.UnsignedIntEncodedValue;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.util.EdgeIteratorState;
import com.google.common.primitives.Longs;
import com.graphhopper.util.PMap;

import java.util.List;

public class CustomCarFlagEncoder extends CarFlagEncoder {

    private UnsignedIntEncodedValue[] stableIdEnc = new UnsignedIntEncodedValue[8];
    private UnsignedIntEncodedValue[] reverseStableIdEnc = new UnsignedIntEncodedValue[8];

    public CustomCarFlagEncoder(PMap configuration) {
        super(configuration);
        super.restrictedValues.remove("private");
    }

    @Override
    public void createEncodedValues(List<EncodedValue> registerNewEncodedValue, String prefix, int index) {
        super.createEncodedValues(registerNewEncodedValue, prefix, index);
        for (int i=0; i<8; i++) {
            stableIdEnc[i] = new UnsignedIntEncodedValue("stable-id-byte-"+i, 8, false);
            registerNewEncodedValue.add(stableIdEnc[i]);
        }
        for (int i=0; i<8; i++) {
            reverseStableIdEnc[i] = new UnsignedIntEncodedValue("reverse-stable-id-byte-"+i, 8, false);
            registerNewEncodedValue.add(reverseStableIdEnc[i]);
        }
    }

    final String getStableId(boolean reverse, EdgeIteratorState edge) {
        byte[] stableId = new byte[8];
        UnsignedIntEncodedValue[] idByte = reverse ? reverseStableIdEnc : stableIdEnc;
        for (int i=0; i<8; i++) {
            stableId[i] = (byte) edge.get(idByte[i]);
        }
        return Long.toUnsignedString(Longs.fromByteArray(stableId));
    }

    final void setStableId(boolean reverse, EdgeIteratorState edge, String stableId) {
        long stableIdLong = Long.parseUnsignedLong(stableId);
        byte[] stableIdBytes = Longs.toByteArray(stableIdLong);

        if (stableIdBytes.length != 8)
            throw new IllegalArgumentException("stable ID must be 8 bytes: " + stableId);

        UnsignedIntEncodedValue[] idBytes = reverse ? reverseStableIdEnc : stableIdEnc;
        for (int i=0; i<8; i++) {
            edge.set(idBytes[i], Byte.toUnsignedInt(stableIdBytes[i]));
        }
    }

}
