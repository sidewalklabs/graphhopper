package com.graphhopper.http.cli;

import com.google.common.collect.Lists;
import com.graphhopper.GraphHopper;
import com.graphhopper.http.GraphHopperManaged;
import com.graphhopper.http.GraphHopperServerConfiguration;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.RoadClass;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.DefaultFlagEncoderFactory;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.swl.CustomGraphHopperOSM;
import com.graphhopper.swl.StableIdEncodedValues;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.PointList;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ExportCommand extends ConfiguredCommand<GraphHopperServerConfiguration> {
    private static final Logger LOG = LoggerFactory.getLogger(ExportCommand.class);

    private static final String FLAG_ENCODERS = "car,bike,foot";
    private static final List<String> HIGHWAY_FILTER_TAGS = Lists.newArrayList("bridleway", "steps");
    private static final String COLUMN_HEADERS = "\"edgeId\",\"stableEdgeId\",\"startVertex\",\"endVertex\"," +
            "\"startLat\",\"startLon\",\"endLat\",\"endLon\",\"geometry\",\"streetName\",\"distance\",\"osmid\"," +
            "\"speed\",\"flags\",\"lanes\",\"highway\"";


    public ExportCommand() {
        super("export", "Generates street network CSV file from a GH graph");
    }

    @Override
    protected void run(Bootstrap<GraphHopperServerConfiguration> bootstrap, Namespace namespace,
                       GraphHopperServerConfiguration configuration) {
        final GraphHopperManaged graphHopper = new GraphHopperManaged(configuration.getGraphHopperConfiguration(),
                bootstrap.getObjectMapper());
        GraphHopper configuredGraphHopper = graphHopper.getGraphHopper();
        if (!configuredGraphHopper.load(configuredGraphHopper.getGraphHopperLocation())) {
            throw new RuntimeException("Couldn't load existing GH graph at " +
                    configuredGraphHopper.getGraphHopperLocation());
        }

        String osmWorkingDir = configuredGraphHopper.getGraphHopperLocation() + "/osm";
        String osmFileLocation = configuredGraphHopper.getDataReaderFile();
        EncodingManager encodingManager = EncodingManager.create(new DefaultFlagEncoderFactory(), FLAG_ENCODERS);

        CustomGraphHopperOSM osmTaggedGraphHopper = new CustomGraphHopperOSM(osmFileLocation);
        osmTaggedGraphHopper.setGraphHopperLocation(osmWorkingDir);
        osmTaggedGraphHopper.setEncodingManager(encodingManager);
        osmTaggedGraphHopper.setInMemory();
        osmTaggedGraphHopper.setDataReaderFile(osmFileLocation);
        osmTaggedGraphHopper.importOrLoad();
        osmTaggedGraphHopper.clean();

        writeStreetEdgesCsv(configuredGraphHopper, osmTaggedGraphHopper);
    }

    private static void writeStreetEdgesCsv(GraphHopper configuredGraphHopper,
                                            CustomGraphHopperOSM osmTaggedGraphHopper) {
        GraphHopperStorage graphHopperStorage = configuredGraphHopper.getGraphHopperStorage();
        AllEdgesIterator edgeIterator = graphHopperStorage.getAllEdges();
        NodeAccess nodes = graphHopperStorage.getNodeAccess();

        EncodingManager encodingManager = configuredGraphHopper.getEncodingManager();
        StableIdEncodedValues stableIdEncodedValues = StableIdEncodedValues.fromEncodingManager(encodingManager);
        final EnumEncodedValue<RoadClass> roadClassEnc =
                encodingManager.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
        CarFlagEncoder carFlagEncoder = (CarFlagEncoder)encodingManager.getEncoder("car");
        DecimalEncodedValue avgSpeedEnc = carFlagEncoder.getAverageSpeedEnc();

        File outputFile = new File(configuredGraphHopper.getGraphHopperLocation() + "/street_edges.csv");

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
            // Fetch starting and ending vertices
            int ghEdgeId = edgeIterator.getEdge();
            int startVertex = edgeIterator.getBaseNode();
            int endVertex = edgeIterator.getAdjNode();
            double startLat = nodes.getLat(startVertex);
            double startLon = nodes.getLon(startVertex);
            double endLat = nodes.getLat(endVertex);
            double endLon = nodes.getLon(endVertex);

            // Get edge geometry and distance
            PointList wayGeometry = edgeIterator.fetchWayGeometry(FetchMode.ALL);
            String geometryString = wayGeometry.toLineString(false).toString();
            long distanceMeters = Math.round(wayGeometry.calcDistance(new DistanceCalcEarth()));

            // Parse OSM highway type and street name, and grab encoded stable IDs for both edge directions
            String highwayTag = edgeIterator.get(roadClassEnc).toString();
            String streetName = edgeIterator.getName();
            String forwardStableEdgeId = stableIdEncodedValues.getStableId(false, edgeIterator);
            String backwardStableEdgeId = stableIdEncodedValues.getStableId(true, edgeIterator);

            // Convert GH's km/h speed to cm/s to match R5's implementation
            int speedcms = (int)(edgeIterator.get(avgSpeedEnc) / 3.6 * 100);

            // Convert GH's distance in meters to millimeters to match R5's implementation
            long distanceMillimeters = distanceMeters * 1000;

            // Fetch OSM ID and accessibility flags for each edge direction
            // Returned flags are from the set {ALLOWS_CAR, ALLOWS_BIKE, ALLOWS_PEDESTRIAN}
            long osmId = osmTaggedGraphHopper.getOsmIdForGhEdge(edgeIterator.getEdge());
            String forwardFlags = osmTaggedGraphHopper.getFlagsForGhEdge(ghEdgeId, false);
            String backwardFlags = osmTaggedGraphHopper.getFlagsForGhEdge(ghEdgeId, true);

            // Calculate number of lanes for edge, as done in R5, based on OSM tags + edge direction
            int overallLanes = parseLanesTag(osmId, osmTaggedGraphHopper, "lanes");
            int forwardLanes = parseLanesTag(osmId, osmTaggedGraphHopper, "lanes:forward");
            int backwardLanes = parseLanesTag(osmId, osmTaggedGraphHopper, "lanes:backward");

            if (!backwardFlags.contains("ALLOWS_CAR")) {
                backwardLanes = 0;
            }
            if (backwardLanes == -1) {
                if (overallLanes != -1) {
                    if (forwardLanes != -1) {
                        backwardLanes = overallLanes - forwardLanes;
                    }
                }
            }

            if (!forwardFlags.contains("ALLOWS_CAR")) {
                forwardLanes = 0;
            }
            if (forwardLanes == -1) {
                if (overallLanes != -1) {
                    if (backwardLanes != -1) {
                        forwardLanes = overallLanes - backwardLanes;
                    } else if (forwardFlags.contains("ALLOWS_CAR")) {
                        forwardLanes = overallLanes / 2;
                    }
                }
            }

            // Copy R5's logic; filter out edges with unwanted highway tags and negative OSM IDs
            // todo: do negative OSM ids happen in GH? This might have been R5-specific
            if (!HIGHWAY_FILTER_TAGS.contains(highwayTag) && osmId >= 0) {
                // Print line for each edge direction
                printStream.println(toString(ghEdgeId, forwardStableEdgeId, startVertex, endVertex,
                        startLat, startLon, endLat, endLon, geometryString, streetName,
                        distanceMillimeters, osmId, speedcms, forwardFlags, forwardLanes, highwayTag));
                printStream.println(toString(ghEdgeId, backwardStableEdgeId, endVertex, startVertex,
                        endLat, endLon, startLat, startLon, geometryString, streetName,
                        distanceMillimeters, osmId, speedcms, backwardFlags, backwardLanes, highwayTag));
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
}
