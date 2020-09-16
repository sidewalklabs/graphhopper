package com.graphhopper.stableid;

import com.google.common.base.Charsets;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.primitives.Longs;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.RoadClass;
import com.graphhopper.routing.ev.UnsignedIntEncodedValue;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.AngleCalc;
import com.graphhopper.util.EdgeIteratorState;

public class StableIdEncodedValues {

    private UnsignedIntEncodedValue[] stableIdEnc = new UnsignedIntEncodedValue[8];
    private UnsignedIntEncodedValue[] reverseStableIdEnc = new UnsignedIntEncodedValue[8];
    private EnumEncodedValue<RoadClass> roadClassEnc;

    private StableIdEncodedValues(EncodingManager encodingManager) {
        this.roadClassEnc = encodingManager.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
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

    public final String getStableId(boolean reverse, EdgeIteratorState edge) {
        byte[] stableId = new byte[8];
        UnsignedIntEncodedValue[] idByte = reverse ? reverseStableIdEnc : stableIdEnc;
        for (int i=0; i<8; i++) {
            stableId[i] = (byte) edge.get(idByte[i]);
        }
        return Long.toUnsignedString(Longs.fromByteArray(stableId));
    }

    public final void setStableId(boolean reverse, EdgeIteratorState edge, NodeAccess nodes) {
        byte[] stableId = calculateStableEdgeId(reverse, edge, this.roadClassEnc, nodes);
        if (stableId.length != 8)
            throw new IllegalArgumentException("stable ID must be 8 bytes: " + new String(stableId));

        UnsignedIntEncodedValue[] idBytes = reverse ? reverseStableIdEnc : stableIdEnc;
        for (int i=0; i<8; i++) {
            edge.set(idBytes[i], Byte.toUnsignedInt(stableId[i]));
        }
    }

    private static byte[] calculateStableEdgeId(boolean reverse, EdgeIteratorState edge,
                                                EnumEncodedValue<RoadClass> roadClassEnc, NodeAccess nodes) {
        String highwayTag = edge.get(roadClassEnc).toString();

        // Because GH edges are technically bi-directional, swap start/end nodes if calculating reverse ID
        int startVertex = reverse ? edge.getAdjNode() : edge.getBaseNode();
        int endVertex = reverse ? edge.getBaseNode() : edge.getAdjNode();
        double startLat = nodes.getLat(startVertex);
        double startLon = nodes.getLon(startVertex);
        double endLat = nodes.getLat(endVertex);
        double endLon = nodes.getLon(endVertex);

        return calculateStableEdgeId(highwayTag, startLat, startLon, endLat, endLon);
    }

    private static byte[] calculateStableEdgeId(String highwayTag, double startLat, double startLon,
                                                double endLat, double endLon) {
        int formOfWay = getFormOfWay(highwayTag);
        long bearing = Math.round(AngleCalc.ANGLE_CALC.calcAzimuth(startLat, startLon, endLat, endLon));

        String hashString = String.format("Reference %d %.6f %.6f %.6f %.6f %d",
                formOfWay, startLon, startLat, endLon, endLat, bearing);

        HashCode hc = Hashing.farmHashFingerprint64().hashString(hashString, Charsets.UTF_8);
        return hc.asBytes();
    }

    // Based off of shared streets' definition of "form of way"
    private static int getFormOfWay(String highwayTag) {
        highwayTag = highwayTag == null ? "" : highwayTag;
        switch (highwayTag) {
            case "motorway":
                return 1;
            case "primary":
            case "trunk":
                return 2;
            case "secondary":
            case "tertiary":
            case "residential":
            case "unclassified":
                return 3;
            case "roundabout":
                return 4;
            default:
                return 7;
        }
    }
}
