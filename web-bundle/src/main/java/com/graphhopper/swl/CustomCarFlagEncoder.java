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

import javax.xml.bind.DatatypeConverter;
import java.util.List;

public class CustomCarFlagEncoder extends CarFlagEncoder {

    private UnsignedIntEncodedValue[] stableIdByte = new UnsignedIntEncodedValue[16];
    private UnsignedIntEncodedValue[] reverseStableIdByte = new UnsignedIntEncodedValue[16];

    public CustomCarFlagEncoder() {
        super();
        super.restrictedValues.remove("private");
    }

    @Override
    public void createEncodedValues(List<EncodedValue> registerNewEncodedValue, String prefix, int index) {
        super.createEncodedValues(registerNewEncodedValue, prefix, index);
        for (int i=0; i<16; i++) {
            stableIdByte[i] = new UnsignedIntEncodedValue("stable-id-byte-"+i, 8, false);
            registerNewEncodedValue.add(stableIdByte[i]);
        }
        for (int i=0; i<16; i++) {
            reverseStableIdByte[i] = new UnsignedIntEncodedValue("reverse-stable-id-byte-"+i, 8, false);
            registerNewEncodedValue.add(reverseStableIdByte[i]);
        }
    }

    final String getStableId(boolean reverse, EdgeIteratorState edge) {
        byte[] stableId = new byte[16];
        UnsignedIntEncodedValue[] idByte = reverse ? reverseStableIdByte : stableIdByte;
        for (int i=0; i<16; i++) {
            stableId[i] = (byte) edge.get(idByte[i]);
        }
        return DatatypeConverter.printHexBinary(stableId).toLowerCase();
    }

    final void setStableId(boolean reverse, EdgeIteratorState edge, String stableId) {
        byte[] stableIdBytes = DatatypeConverter.parseHexBinary(stableId);

        if (stableIdBytes.length != 16)
            throw new IllegalArgumentException("stable ID must be 16 bytes: " + DatatypeConverter.printHexBinary(stableIdBytes));

        UnsignedIntEncodedValue[] idByte = reverse ? reverseStableIdByte : stableIdByte;
        for (int i=0; i<16; i++) {
            edge.set(idByte[i], Byte.toUnsignedInt(stableIdBytes[i]));
        }
    }

}
