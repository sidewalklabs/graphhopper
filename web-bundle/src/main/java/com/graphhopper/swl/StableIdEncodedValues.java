package com.graphhopper.swl;

import com.google.common.primitives.Longs;
import com.graphhopper.routing.ev.UnsignedIntEncodedValue;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.EdgeIteratorState;

public class StableIdEncodedValues {

    private UnsignedIntEncodedValue[] stableIdEnc = new UnsignedIntEncodedValue[8];
    private UnsignedIntEncodedValue[] reverseStableIdEnc = new UnsignedIntEncodedValue[8];

    public void createEncodedValues(EncodingManager.Builder emBuilder) {
        for (int i=0; i<8; i++) {
            stableIdEnc[i] = new UnsignedIntEncodedValue("stable-id-byte-"+i, 8, false);
            emBuilder.add(stableIdEnc[i]);
        }
        for (int i=0; i<8; i++) {
            reverseStableIdEnc[i] = new UnsignedIntEncodedValue("reverse-stable-id-byte-"+i, 8, false);
            emBuilder.add(reverseStableIdEnc[i]);
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
