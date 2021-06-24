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
package com.replica;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.protobuf.Timestamp;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.gtfs.GraphHopperGtfs;
import com.graphhopper.gtfs.PtRouter;
import com.graphhopper.gtfs.PtRouterImpl;
import com.graphhopper.gtfs.RealtimeFeed;
import com.graphhopper.http.GraphHopperApplication;
import com.graphhopper.http.GraphHopperBundle;
import com.graphhopper.http.GraphHopperManaged;
import com.graphhopper.http.GraphHopperServerConfiguration;
import com.graphhopper.http.cli.GtfsLinkMapperCommand;
import com.graphhopper.http.cli.ImportCommand;
import com.graphhopper.jackson.GraphHopperConfigModule;
import com.graphhopper.jackson.Jackson;
import com.graphhopper.routing.GHMatrixAPI;
import com.graphhopper.routing.MatrixAPI;
import com.graphhopper.util.Helper;
import io.dropwizard.cli.Cli;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.util.JarLocation;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import router.RouterOuterClass;

import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests the entire app, not the resource, so that the plugging-together
 * of stuff (which is different for PT than for the rest) is under test, too.
 */
@ExtendWith(DropwizardExtensionsSupport.class)
public class RouterServerTest {
    private static final Logger logger = LoggerFactory.getLogger(RouterServerTest.class);
    private static final String TARGET_DIR = "./target/gtfs-app-gh/";
    private static final String TRANSIT_DATA_DIR = "transit_data/";
    private static final String TEST_GRAPHHOPPER_CONFIG_PATH = "../test_gh_config.yaml";
    private static final String TEST_REGION_NAME = "mini_kc";
    private static final String TEST_GTFS_FILE_NAME = "mini_kc_gtfs.tar";

    private static final Timestamp EARLIEST_DEPARTURE_TIME =
            Timestamp.newBuilder().setSeconds(Instant.parse("2017-07-21T08:25:00Z").toEpochMilli() / 1000).build();
    final router.RouterOuterClass.PtRouteRequest PT_REQUEST =
            createPtRequest(38.96637569955874, -94.70833304570988,
                    38.959204519370815, -94.69174071738964, EARLIEST_DEPARTURE_TIME);

    private static GraphHopperConfig graphHopperConfiguration = null;
    private static router.RouterGrpc.RouterBlockingStub routerStub = null;

    private static GraphHopperManaged loadGraphhopper() throws Exception {
        ObjectMapper yaml = Jackson.initObjectMapper(new ObjectMapper(new YAMLFactory()));
        yaml.registerModule(new GraphHopperConfigModule());
        JsonNode yamlNode = yaml.readTree(new File(TEST_GRAPHHOPPER_CONFIG_PATH));
        graphHopperConfiguration = yaml.convertValue(yamlNode.get("graphhopper"), GraphHopperConfig.class);
        ObjectMapper json = Jackson.newObjectMapper();
        GraphHopperManaged graphHopperManaged = new GraphHopperManaged(graphHopperConfiguration, json);
        graphHopperManaged.start();
        return graphHopperManaged;
    }

    private static void startTestServer() throws Exception {
        GraphHopperManaged graphHopperManaged = loadGraphhopper();

        // Grab instances of auto/bike/ped router and PT router (if applicable)
        GraphHopper graphHopper = graphHopperManaged.getGraphHopper();
        PtRouter ptRouter = null;
        if (graphHopper instanceof GraphHopperGtfs) {
            ptRouter = new PtRouterImpl(graphHopper.getTranslationMap(), graphHopper.getGraphHopperStorage(),
                    graphHopper.getLocationIndex(), ((GraphHopperGtfs) graphHopper).getGtfsStorage(),
                    RealtimeFeed.empty(((GraphHopperGtfs) graphHopper).getGtfsStorage()),
                    graphHopper.getPathDetailsBuilderFactory());
        }

        // Create matrix API instance
        MatrixAPI matrixAPI = new GHMatrixAPI(graphHopper, graphHopperConfiguration);

        // Load GTFS link mapping and GTFS info maps for use in building responses
        Map<String, String> gtfsLinkMappings = null;
        Map<String, List<String>> gtfsRouteInfo = null;
        Map<String, String> gtfsFeedIdMapping = null;

        File linkMappingsDbFile = new File("transit_data/gtfs_link_mappings.db");
        if (linkMappingsDbFile.exists()) {
            DB db = DBMaker.newFileDB(linkMappingsDbFile).readOnly().make();
            gtfsLinkMappings = db.getHashMap("gtfsLinkMappings");
            gtfsRouteInfo = db.getHashMap("gtfsRouteInfo");
            gtfsFeedIdMapping = db.getHashMap("gtfsFeedIdMap");
        }

        String uniqueName = InProcessServerBuilder.generateName();
        Server server = InProcessServerBuilder.forName(uniqueName)
                .directExecutor() // directExecutor is fine for unit tests
                .addService(new com.replica.RouterImpl(graphHopper, ptRouter, matrixAPI, gtfsLinkMappings,
                        gtfsRouteInfo, gtfsFeedIdMapping, null, TEST_REGION_NAME))
                .addService(ProtoReflectionService.newInstance())
                .build().start();
        ManagedChannel channel = InProcessChannelBuilder.forName(uniqueName)
                .directExecutor()
                .build();

        routerStub = router.RouterGrpc.newBlockingStub(channel);
    }

    @BeforeAll
    public static void setUp() throws Exception {
        // Fresh target + transit_dir directories
        Helper.removeDir(new File(TARGET_DIR));
        Helper.removeDir(new File(TRANSIT_DATA_DIR));
        // Create new empty directory for GTFS/OSM resources
        File transitDataDir = new File(TRANSIT_DATA_DIR);
        if (transitDataDir.exists()) {
            throw new IllegalStateException(TRANSIT_DATA_DIR + " directory should not already exist.");
        }
        Preconditions.checkState(transitDataDir.mkdir(), "could not create directory " + TRANSIT_DATA_DIR);

        // Setup necessary mock
        final JarLocation location = mock(JarLocation.class);
        when(location.getVersion()).thenReturn(Optional.of("1.0.0"));

        // Add commands you want to test
        final Bootstrap<GraphHopperServerConfiguration> bootstrap = new Bootstrap<>(new GraphHopperApplication());
        bootstrap.addBundle(new GraphHopperBundle());
        bootstrap.addCommand(new ImportCommand());
        bootstrap.addCommand(new GtfsLinkMapperCommand());

        // Run commands to build graph and GTFS link mappings for test region
        Cli cli = new Cli(location, bootstrap, System.out, System.err);
        cli.run("import", TEST_GRAPHHOPPER_CONFIG_PATH);
        cli.run("gtfs_links", TEST_GRAPHHOPPER_CONFIG_PATH);

        startTestServer();
    }

    @AfterAll
    public static void cleanUp() {
        Helper.removeDir(new File(TARGET_DIR));
        Helper.removeDir(new File(TRANSIT_DATA_DIR));
    }

    private static router.RouterOuterClass.StreetRouteRequest createStreetRequest(double startLat, double startLon,
                                                                                  double endLat, double endLon, String mode) {
        return router.RouterOuterClass.StreetRouteRequest.newBuilder()
                .addPoints(0, router.RouterOuterClass.Point.newBuilder()
                        .setLat(startLat)
                        .setLon(startLon)
                        .build())
                .addPoints(1, router.RouterOuterClass.Point.newBuilder()
                        .setLat(endLat)
                        .setLon(endLon)
                        .build())
                .setAlternateRouteMaxPaths(5)
                .setAlternateRouteMaxWeightFactor(2.0)
                .setAlternateRouteMaxShareFactor(0.4)
                .setProfile(mode)
                .build();
    }

    private static router.RouterOuterClass.PtRouteRequest createPtRequest(double startLat, double startLon,
                                                                          double endLat, double endLon, Timestamp earliestDepartureTime) {
        return router.RouterOuterClass.PtRouteRequest.newBuilder()
                .addPoints(0, router.RouterOuterClass.Point.newBuilder()
                        .setLat(startLat)
                        .setLon(startLon)
                        .build())
                .addPoints(1, router.RouterOuterClass.Point.newBuilder()
                        .setLat(endLat)
                        .setLon(endLon)
                        .build())
                .setEarliestDepartureTime(earliestDepartureTime)
                .setLimitSolutions(4)
                .setMaxProfileDuration(10)
                .setBetaWalkTime(1.5)
                .setLimitStreetTimeSeconds(1440)
                .setUsePareto(false)
                .setBetaTransfers(1440000)
                .build();
    }

    @Test
    public void testPublicTransitQuery() {
        final router.RouterOuterClass.PtRouteReply response = routerStub.routePt(PT_REQUEST);

        // Check details of Path are set correctly
        assertEquals(1, response.getPathsList().size());
        RouterOuterClass.PtPath path = response.getPaths(0);
        assertEquals(1, path.getPtLegsList().size());
        assertEquals(2, path.getFootLegsList().size());
        assertTrue(path.getDistanceMeters() > 0);
        assertTrue(path.getDurationMillis() > 0);

        // Check that foot legs contain proper info
        List<String> observedTravelSegmentTypes = Lists.newArrayList();
        List<String> expectedTravelSegmentTypes = Lists.newArrayList("ACCESS", "EGRESS");
        double observedDistanceMeters = 0;
        for (RouterOuterClass.FootLeg footLeg : path.getFootLegsList()) {
            assertTrue(footLeg.getStableEdgeIdsCount() > 0);
            assertTrue(footLeg.getArrivalTime().getSeconds() > footLeg.getDepartureTime().getSeconds());
            assertTrue(footLeg.getDistanceMeters() > 0);
            assertFalse(footLeg.getTravelSegmentType().isEmpty());
            observedTravelSegmentTypes.add(footLeg.getTravelSegmentType());
            observedDistanceMeters += footLeg.getDistanceMeters();
        }
        assertEquals(expectedTravelSegmentTypes, observedTravelSegmentTypes);
        // todo: once PT legs have distances, incorporate those in this check
        assertEquals(path.getDistanceMeters(), observedDistanceMeters);

        // Check that PT leg contains proper info
        RouterOuterClass.PtLeg ptLeg = path.getPtLegs(0);
        assertTrue(ptLeg.getArrivalTime().getSeconds() > ptLeg.getDepartureTime().getSeconds());
        assertTrue(ptLeg.getStableEdgeIdsCount() > 0); // check that the GTFS link mapper worked

        assertFalse(ptLeg.getTripId().isEmpty());
        assertFalse(ptLeg.getRouteId().isEmpty());
        assertFalse(ptLeg.getAgencyName().isEmpty());
        assertFalse(ptLeg.getRouteShortName().isEmpty());
        assertFalse(ptLeg.getRouteLongName().isEmpty());
        assertFalse(ptLeg.getRouteType().isEmpty());
        assertFalse(ptLeg.getDirection().isEmpty());

        // Check stops in PT leg
        assertTrue(ptLeg.getStopsList().size() > 0);
        for (RouterOuterClass.Stop stop : ptLeg.getStopsList()) {
            assertFalse(stop.getStopId().isEmpty());
            assertTrue(stop.getStopId().startsWith(TEST_GTFS_FILE_NAME));
            assertFalse(stop.getStopName().isEmpty());
            assertTrue(stop.hasPoint());
        }
    }

    /*
    @Test
    public void testWalkQuery() {
        final Response response = clientTarget(app, "/route")
                .queryParam("point", "36.914893,-116.76821")
                .queryParam("point", "36.914944,-116.761472")
                .queryParam("profile", "foot")
                .request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        GHResponse ghResponse = response.readEntity(GHResponse.class);
        assertFalse(ghResponse.hasErrors());
    }

    @Test
    public void testNoPoints() {
        final Response response = clientTarget(app, "/route")
                .queryParam("vehicle", "pt")
                .request().buildGet().invoke();
        assertEquals(400, response.getStatus());
    }

    @Test
    public void testOnePoint() {
        final Response response = clientTarget(app, "/route")
                .queryParam("point", "36.914893,-116.76821")
                .queryParam("vehicle", "pt")
                .queryParam("pt.earliest_departure_time", "2007-01-01T08:00:00Z")
                .request().buildGet().invoke();
        assertEquals(400, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        assertEquals("query param point size must be between 2 and 2", json.get("message").asText());
    }

    @Test
    public void testBadPoints() {
        final Response response = clientTarget(app, "/route")
                .queryParam("point", "pups")
                .queryParam("vehicle", "pt")
                .queryParam("pt.earliest_departure_time", "2007-01-01T08:00:00Z")
                .request().buildGet().invoke();
        assertEquals(400, response.getStatus());
    }

    @Test
    public void testNoTime() {
        final Response response = clientTarget(app, "/route")
                .queryParam("point", "36.914893,-116.76821") // NADAV stop
                .queryParam("point", "36.914944,-116.761472") //NANAA stop
                .queryParam("vehicle", "pt")
                .request().buildGet().invoke();
        assertEquals(400, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        // Would prefer a "must not be null" message here, but is currently the same as for a bad time (see below).
        // I DO NOT want to manually catch this, I want to figure out how to fix this upstream, or live with it.
        assertTrue(json.get("message").asText().startsWith("query param pt.earliest_departure_time must"));
    }

    @Test
    public void testBadTime() {
        final Response response = clientTarget(app, "/route")
                .queryParam("point", "36.914893,-116.76821") // NADAV stop
                .queryParam("point", "36.914944,-116.761472") //NANAA stop
                .queryParam("vehicle", "pt")
                .queryParam("pt.earliest_departure_time", "wurst")
                .request().buildGet().invoke();
        assertEquals(400, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        assertEquals("query param pt.earliest_departure_time must be in a ISO-8601 format.", json.get("message").asText());
    }

    @Test
    public void testInfo() {
        final Response response = clientTarget(app, "/info")
                .request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        InfoResource.Info info = response.readEntity(InfoResource.Info.class);
        assertTrue(info.supported_vehicles.contains("pt"));
        assertTrue(info.profiles.stream().anyMatch(p -> p.name.equals("pt")));
    }

    public static WebTarget clientTarget(DropwizardAppExtension<? extends Configuration> app, String path) {
        path = prefixPathWithSlash(path);
        return app.client().target(clientUrl(app, path));
    }

    public static String clientUrl(DropwizardAppExtension<? extends Configuration> app, String path) {
        path = prefixPathWithSlash(path);
        return "http://localhost:" + app.getLocalPort() + path;
    }

    private static String prefixPathWithSlash(String path) {
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return path;
    }
    */
}
