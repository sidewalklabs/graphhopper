package com.graphhopper.swl;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;

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
        final EnumEncodedValue<RoadClass> roadClassEnc =
                encodingManager.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
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
            boolean isReverse = edgeIterator.get(EdgeIteratorState.REVERSE_STATE);
            int startVertex = edgeIterator.getBaseNode();
            int endVertex = edgeIterator.getAdjNode();
            double startLat = nodes.getLat(startVertex);
            double startLon = nodes.getLon(startVertex);
            double endLat = nodes.getLat(endVertex);
            double endLon = nodes.getLon(endVertex);

            PointList wayGeometry = edgeIterator.fetchWayGeometry(FetchMode.ALL);
            String geometryString = wayGeometry.toLineString(false).toString();
            long distanceMeters = Math.round(wayGeometry.calcDistance(new DistanceCalcEarth()));
            String highwayTag = edgeIterator.get(roadClassEnc).toString();
            String streetName = edgeIterator.getName();

            // Convert GH's km/h speed to cm/s to match R5's implementation
            int speedcms = (int)(edgeIterator.get(avgSpeedEnc) / 3.6 * 100);

            // Convert GH's distance in meters to millimeters to match R5's implementation
            long distanceMillimeters = distanceMeters * 1000;

            long osmId = graphHopper.getOsmIdForGhEdge(edgeIterator.getEdge());
            String flags = graphHopper.getFlagsForGhEdge(ghEdgeId, isReverse);
            String stableEdgeId = calculateStableEdgeId(highwayTag, startLat, startLon, endLat, endLon);

            // Calculate number of lanes for edge, as done in R5, based on OSM tags + edge direction
            int overallLanes = parseLanesTag(osmId, graphHopper, "lanes");
            int forwardLanes = parseLanesTag(osmId, graphHopper, "lanes:forward");
            int backwardLanes = parseLanesTag(osmId, graphHopper, "lanes:backward");

            if (isReverse) {
                if (!flags.contains("ALLOWS_CAR")) {
                    backwardLanes = 0;
                }
                if (backwardLanes == -1) {
                    if (overallLanes != -1) {
                        if (forwardLanes != -1) {
                            backwardLanes = overallLanes - forwardLanes;
                        }
                    }
                }
            } else {
                if (!flags.contains("ALLOWS_CAR")) {
                    forwardLanes = 0;
                }
                if (forwardLanes == -1) {
                    if (overallLanes != -1) {
                        if (backwardLanes != -1) {
                            forwardLanes = overallLanes - backwardLanes;
                        } else if (flags.contains("ALLOWS_CAR")) {
                            forwardLanes = overallLanes / 2;
                        }
                    }
                }
            }

            // Copy R5's logic; filter out edges with unwanted highway tags, negative OSM IDs, and reverse highway links
            if (!HIGHWAY_FILTER_TAGS.contains(highwayTag) && osmId >= 0) {
                if (!(isReverse && highwayTag.equals("motorway"))) {
                    printStream.println(toString(ghEdgeId, stableEdgeId, startVertex, endVertex, startLat, startLon,
                            endLat, endLon, geometryString, streetName, distanceMillimeters, osmId, speedcms, flags,
                            isReverse ? backwardLanes : forwardLanes, highwayTag));
                }
            }
        }

        printStream.close();
        LOG.info("Done writing street network to CSV");
        assert(outputFile.exists());
    }

    private static String toString(int ghEdgeId, String stableEdgeId, int startVertex, int endVertex, double startLat,
                                   double startLon, double endLat, double endLon, String geometry, String streetName,
                                   long distance, long osmId, int speed, String flags, int lanes, String highway) {
        return String.format("%d,\"%s\",%d,%d,%f,%f,%f,%f,\"%s\",\"%s\",%d,%d,%d,\"%s\",%d,\"%s\"",
                ghEdgeId, stableEdgeId, startVertex, endVertex, startLat, startLon, endLat, endLon, geometry,
                streetName, distance, osmId, speed, flags, lanes, highway
        );
    }

    // Taken from R5's lane parsing logic. See EdgeServiceServer.java
    private static int parseLanesTag(long osmId, CustomGraphHopperOSM graphHopper, String laneTag) {
        int result = -1;
        Map<String, String> laneTagsOnEdge = graphHopper.getLanesTag(osmId);
        if (laneTagsOnEdge != null) {
            if (laneTagsOnEdge.containsKey(laneTag)) {
                try {
                    return parseLanesTag(laneTagsOnEdge.get(laneTag));
                } catch (NumberFormatException ex) {
                    LOG.warn("way {}: Unable to parse lanes value as number {}", osmId, laneTag);
                }
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

    private static String calculateStableEdgeId(String highwayTag, double startLat, double startLon,
                                              double endLat, double endLon) {
        int formOfWay = getFormOfWay(highwayTag);
        long bearing = Math.round(Helper.ANGLE_CALC.calcAzimuth(startLat, startLon, endLat, endLon));

        String hashString = String.format("Reference %d %.6f %.6f %.6f %.6f %d",
                formOfWay, startLon, startLat, endLon, endLat, bearing);

        HashCode hc = Hashing.farmHashFingerprint64().hashString(hashString, Charsets.UTF_8);
        return Long.toUnsignedString(hc.asLong());
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
