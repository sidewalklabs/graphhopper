package com.graphhopper.swl;

import com.google.common.primitives.Longs;
import com.graphhopper.routing.ev.UnsignedIntEncodedValue;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint3D;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class StableIdEncodedValues {

    private UnsignedIntEncodedValue[] stableIdEnc = new UnsignedIntEncodedValue[8];
    private UnsignedIntEncodedValue[] reverseStableIdEnc = new UnsignedIntEncodedValue[8];

    private StableIdEncodedValues(EncodingManager encodingManager) {
        for (int i=0; i<8; i++) {
            stableIdEnc[i] = (UnsignedIntEncodedValue) encodingManager.getIntEncodedValue("stable-id-byte-"+i);
        }
        for (int i=0; i<8; i++) {
            reverseStableIdEnc[i] = (UnsignedIntEncodedValue) encodingManager.getIntEncodedValue("reverse-stable-id-byte-"+i);
        }
    }

    public static StableIdEncodedValues fromEncodingManager(EncodingManager encodingManager) {
        return new StableIdEncodedValues(encodingManager);
    }

    public static void createAndAddEncodedValues(EncodingManager.Builder emBuilder) {
        for (int i=0; i<8; i++) {
            emBuilder.add(new UnsignedIntEncodedValue("stable-id-byte-"+i, 8, false));
        }
        for (int i=0; i<8; i++) {
            emBuilder.add(new UnsignedIntEncodedValue("reverse-stable-id-byte-"+i, 8, false));
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

    public final void setStableId(boolean reverse, EdgeIteratorState edge) {
        byte[] stableId = calculateStableEdgeId(edge);
        if (stableId.length != 8)
            throw new IllegalArgumentException("stable ID must be 8 bytes: " + new String(stableId));

        UnsignedIntEncodedValue[] idBytes = reverse ? reverseStableIdEnc : stableIdEnc;
        for (int i=0; i<8; i++) {
            edge.set(idBytes[i], Byte.toUnsignedInt(stableId[i]));
        }
    }

    private static byte[] calculateStableEdgeId(EdgeIteratorState edge) {
        // todo: how to get type of street?
        String hashString = "Reference";

        PointList geometry = edge.fetchWayGeometry(FetchMode.ALL);
        GHPoint3D baseNode = geometry.get(edge.getBaseNode());
        GHPoint3D adjNode = geometry.get(edge.getAdjNode());
        double startLat = baseNode.getLat();
        double startLong = baseNode.getLon();
        double endLat = adjNode.getLat();
        double endLong = adjNode.getLon();
        hashString += String.format(" %.5f %.5f %.5f %.5f", startLong, startLat, endLong, endLat);

        // per https://discuss.graphhopper.com/t/how-to-get-the-heading-clockwise-from-north-given-two-points/1357
        hashString += String.format(" %d", Math.round(Helper.ANGLE_CALC.calcAzimuth(startLat, startLong, endLat, endLong)));
        hashString += String.format(" %d", Math.round(geometry.calcDistance(new DistanceCalcEarth())));

        try {
            MessageDigest md5MessageDigest = MessageDigest.getInstance("MD5");
            return md5MessageDigest.digest(hashString.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Couldn't load MD5 hashing MessageDigest!");
        }
    }
}
