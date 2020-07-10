package com.graphhopper.swl;

import com.google.common.collect.Lists;
import com.google.common.primitives.Longs;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.RoadClass;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.DefaultFlagEncoderFactory;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;

public class ExportGraphHopperStreetEdgesFromOsm {
    private static final Logger LOG = LoggerFactory.getLogger(ExportGraphHopperStreetEdgesFromOsm.class);

    private static final String FLAG_ENCODERS = "car,bike,foot";
    private static final List<String> HIGHWAY_FILTER_TAGS = Lists.newArrayList("bridleway", "steps");
    private static final String COLUMN_HEADERS = "\"edgeId\",\"stableEdgeId\",\"startVertex\",\"endVertex\"," +
            "\"startLat\",\"startLon\",\"endLat\",\"endLon\",\"geometry\",\"streetName\",\"distance\",\"osmid\"," +
            "\"speed\",\"flags\",\"lanes\",\"highway\"";

    public static void main(String[] args) {
        String ghLocation = args[0];
        String osmLocation = args[1];
        String csvOutputLocation = args[2];
        File outputFile = new File(csvOutputLocation);

        EncodingManager encodingManager = EncodingManager.create(new DefaultFlagEncoderFactory(), FLAG_ENCODERS);

        CustomGraphHopperOSM graphHopper = new CustomGraphHopperOSM(osmLocation);
        graphHopper.setGraphHopperLocation(ghLocation);
        graphHopper.setEncodingManager(encodingManager);
        graphHopper.setInMemory();
        graphHopper.setDataReaderFile(osmLocation);
        graphHopper.importOrLoad();
        graphHopper.clean();

        GraphHopperStorage graphHopperStorage = graphHopper.getGraphHopperStorage();
        AllEdgesIterator edgeIterator = graphHopperStorage.getAllEdges();
        NodeAccess nodes = graphHopperStorage.getNodeAccess();
        final EnumEncodedValue<RoadClass> roadClassEnc = encodingManager.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
        CarFlagEncoder carFlagEncoder = (CarFlagEncoder)encodingManager.getEncoder("car");
        DecimalEncodedValue avgSpeedEnc = carFlagEncoder.getAverageSpeedEnc();

        LOG.info("Writing street edges...");
        OutputStream outputStream;
        try {
            outputStream = new BufferedOutputStream(new FileOutputStream(outputFile));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        PrintStream printStream = new PrintStream(outputStream);
        printStream.println(COLUMN_HEADERS);

        while (edgeIterator.next()) {
            int ghEdgeId = edgeIterator.getEdge();
            int startVertex = edgeIterator.getBaseNode();
            int endVertex = edgeIterator.getAdjNode();
            double startLat = nodes.getLat(startVertex);
            double startLon = nodes.getLon(startVertex);
            double endLat = nodes.getLat(endVertex);
            double endLon = nodes.getLon(endVertex);

            PointList wayGeometry = edgeIterator.fetchWayGeometry(FetchMode.ALL);
            String geometryString = wayGeometry.toLineString(false).toString();
            long distance = Math.round(wayGeometry.calcDistance(new DistanceCalcEarth()));
            String highwayTag = edgeIterator.get(roadClassEnc).toString();
            String streetName = edgeIterator.getName();

            // Convert GH's km/h speed to cm/s to match R5's implementation
            int speedcms = (int)(edgeIterator.get(avgSpeedEnc) / 3.6 * 100);

            long osmId = graphHopper.getOsmIdForGhEdge(edgeIterator.getEdge());
            String flags = graphHopper.getFlagsForGhEdge(ghEdgeId, edgeIterator.get(EdgeIteratorState.REVERSE_STATE));
            long stableEdgeId = calculateStableEdgeId(highwayTag, startLat, startLon, endLat, endLon);
            int lanes = parseLanesTag(osmId, graphHopper);

            // Copy R5's logic; filter out edges with unwanted highway tags, negative OSM IDs, and reverse highway links
            if (!HIGHWAY_FILTER_TAGS.contains(highwayTag) && osmId >= 0) {
                if (!(edgeIterator.get(EdgeIteratorState.REVERSE_STATE) && highwayTag.equals("motorway"))) {
                    printStream.println(toString(ghEdgeId, stableEdgeId, startVertex, endVertex, startLat, startLon, endLat,
                            endLon, geometryString, streetName, distance, osmId, speedcms, flags, lanes, highwayTag));
                }
            }
        }

        printStream.close();
        LOG.info("Done writing street network to CSV");
        assert(outputFile.exists());
    }

    private static String toString(int ghEdgeId, long stableEdgeId, int startVertex, int endVertex, double startLat,
                                   double startLon, double endLat, double endLon, String geometry, String streetName,
                                   long distance, long osmId, int speed, String flags, int lanes, String highway) {
        return String.format("%d,\"%d\",%d,%d,%f,%f,%f,%f,\"%s\",\"%s\",%d,%d,%d,\"%s\",%d,\"%s\"",
                ghEdgeId, stableEdgeId, startVertex, endVertex, startLat, startLon, endLat, endLon, geometry,
                streetName, distance, osmId, speed, flags, lanes, highway
        );
    }

    // Taken from R5's lane parsing logic. See EdgeServiceServer.java
    private static int parseLanesTag(long osmId, CustomGraphHopperOSM graphHopper) {
        int result = -1;
        String lanesTag = graphHopper.getLanesTag(osmId);
        if (lanesTag != null) {
            try {
                return parseLanesTag(lanesTag);
            } catch (NumberFormatException ex) {
                LOG.warn("way {}: Unable to parse lanes value as number {}", osmId, lanesTag);
            }
        }
        return result;
    }

    static int parseLanesTag(String tagValue) {
        double[] values = Arrays.stream(tagValue.split(";"))
                .mapToDouble(Double::parseDouble)
                .toArray();
        Arrays.sort(values);
        double median;
        if (values.length % 2 == 0) {
            median = values[values.length/2-1];
        } else {
            median = values[values.length / 2];
        }
        return (int) median;
    }

    private static long calculateStableEdgeId(String highwayTag, double startLat, double startLon,
                                              double endLat, double endLon) {
        int formOfWay = getFormOfWay(highwayTag);
        long bearing = Math.round(Helper.ANGLE_CALC.calcAzimuth(startLat, startLon, endLat, endLon));

        String hashString = String.format("Reference %d %.6f %.6f %.6f %.6f %d",
                formOfWay, startLon, startLat, endLon, endLat, bearing);

        try {
            MessageDigest md5MessageDigest = MessageDigest.getInstance("MD5");
            byte[] hash = md5MessageDigest.digest(hashString.getBytes(StandardCharsets.UTF_8));
            return Longs.fromByteArray(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Couldn't load MD5 hashing MessageDigest!");
        }
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
