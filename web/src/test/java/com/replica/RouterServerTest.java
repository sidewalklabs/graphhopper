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

import com.google.common.collect.Lists;
import com.google.protobuf.Timestamp;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.gtfs.GraphHopperGtfs;
import com.graphhopper.gtfs.PtRouter;
import com.graphhopper.gtfs.PtRouterImpl;
import com.graphhopper.gtfs.RealtimeFeed;
import com.graphhopper.routing.GHMatrixAPI;
import com.graphhopper.routing.MatrixAPI;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import router.RouterOuterClass;

import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the entire server, not the server implementation itself, so that the plugging-together
 * of stuff (which is different for PT than for the rest) is under test, too.
 */
@ExtendWith(DropwizardExtensionsSupport.class)
public class RouterServerTest extends ReplicaGraphHopperTest {
    private static final Timestamp EARLIEST_DEPARTURE_TIME =
            Timestamp.newBuilder().setSeconds(Instant.parse("2017-07-21T08:25:00Z").toEpochMilli() / 1000).build();
    private static final double[] REQUEST_ODS =
            {38.96637569955874, -94.70833304570988, 38.959204519370815, -94.69174071738964};
    private static final RouterOuterClass.PtRouteRequest PT_REQUEST = createPtRequest();
    private static final RouterOuterClass.StreetRouteRequest AUTO_REQUEST =
            createStreetRequest("car", false);
    private static final RouterOuterClass.StreetRouteRequest AUTO_REQUEST_WITH_ALTERNATIVES =
            createStreetRequest("car", true);
    private static final RouterOuterClass.StreetRouteRequest WALK_REQUEST =
            createStreetRequest("foot", false);

    private static GraphHopperConfig graphHopperConfiguration = null;
    private static router.RouterGrpc.RouterBlockingStub routerStub = null;

    @BeforeAll
    public static void startTestServer() throws Exception {
        // Load Graphhopper using already-built graph files
        graphHopperManaged = loadGraphhopper();

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

        // Start in-process test server + instantiate stub
        String uniqueName = InProcessServerBuilder.generateName();
        InProcessServerBuilder.forName(uniqueName)
                .directExecutor() // directExecutor is fine for unit tests
                .addService(new RouterImpl(graphHopper, ptRouter, matrixAPI, gtfsLinkMappings,
                        gtfsRouteInfo, gtfsFeedIdMapping, null, TEST_REGION_NAME))
                .addService(ProtoReflectionService.newInstance())
                .build().start();
        ManagedChannel channel = InProcessChannelBuilder.forName(uniqueName)
                .directExecutor()
                .build();

        routerStub = router.RouterGrpc.newBlockingStub(channel);
    }

    private static RouterOuterClass.StreetRouteRequest createStreetRequest(String mode, boolean alternatives) {
        return RouterOuterClass.StreetRouteRequest.newBuilder()
                .addPoints(0, RouterOuterClass.Point.newBuilder()
                        .setLat(REQUEST_ODS[0])
                        .setLon(REQUEST_ODS[1])
                        .build())
                .addPoints(1, RouterOuterClass.Point.newBuilder()
                        .setLat(REQUEST_ODS[2])
                        .setLon(REQUEST_ODS[3])
                        .build())
                .setProfile(mode)
                .setAlternateRouteMaxPaths(alternatives ? 5 : 0)
                .setAlternateRouteMaxWeightFactor(2.0)
                .setAlternateRouteMaxShareFactor(0.4)
                .build();
    }

    private static RouterOuterClass.PtRouteRequest createPtRequest() {
        return RouterOuterClass.PtRouteRequest.newBuilder()
                .addPoints(0, RouterOuterClass.Point.newBuilder()
                        .setLat(REQUEST_ODS[0])
                        .setLon(REQUEST_ODS[1])
                        .build())
                .addPoints(1, RouterOuterClass.Point.newBuilder()
                        .setLat(REQUEST_ODS[2])
                        .setLon(REQUEST_ODS[3])
                        .build())
                .setEarliestDepartureTime(EARLIEST_DEPARTURE_TIME)
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
        final RouterOuterClass.PtRouteReply response = routerStub.routePt(PT_REQUEST);

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

    @Test
    public void testAutoQuery() {
        final RouterOuterClass.StreetRouteReply response = routerStub.routeStreetMode(AUTO_REQUEST);
        checkStreetBasedResponse(response, false);
    }

    @Test
    public void testWalkQuery() {
        final RouterOuterClass.StreetRouteReply response = routerStub.routeStreetMode(WALK_REQUEST);
        checkStreetBasedResponse(response, false);
    }

    @Test
    public void testAutoQueryWithAlternatives() {
        final RouterOuterClass.StreetRouteReply response = routerStub.routeStreetMode(AUTO_REQUEST_WITH_ALTERNATIVES);
        checkStreetBasedResponse(response, true);
    }

    private static void checkStreetBasedResponse(RouterOuterClass.StreetRouteReply response, boolean alternatives) {
        assertTrue(alternatives ? response.getPathsList().size() > 1 : response.getPathsList().size() == 1);
        RouterOuterClass.StreetPath path = response.getPaths(0);
        assertTrue(path.getDurationMillis() > 0);
        assertTrue(path.getDistanceMeters() > 0);
        assertTrue(path.getStableEdgeIdsCount() > 0);
        assertEquals(path.getStableEdgeIdsCount(), path.getEdgeDurationsMillisCount());
        int totalDurationMillis = path.getEdgeDurationsMillisList().stream().mapToInt(Long::intValue).sum();
        assertEquals(path.getDurationMillis(), totalDurationMillis);
    }

    @Test
    public void testAutoFasterThanWalk() {
        final RouterOuterClass.StreetRouteReply autoResponse = routerStub.routeStreetMode(AUTO_REQUEST);
        final RouterOuterClass.StreetRouteReply walkResponse = routerStub.routeStreetMode(WALK_REQUEST);
        assertTrue(autoResponse.getPaths(0).getDurationMillis() <
                walkResponse.getPaths(0).getDurationMillis());
    }

    @Test
    public void testBadPointsStreetMode() {
        RouterOuterClass.StreetRouteRequest badAutoRequest = AUTO_REQUEST.toBuilder()
                .setPoints(0, RouterOuterClass.Point.newBuilder().setLat(38.0).setLon(-94.0).build()).build();
        StatusRuntimeException exception =
                assertThrows(StatusRuntimeException.class, () -> {routerStub.routeStreetMode(badAutoRequest);});
        assertSame(exception.getStatus().getCode(), Status.NOT_FOUND.getCode());
    }

    @Test
    public void testBadPointsTransit() {
        RouterOuterClass.PtRouteRequest badPtRequest = PT_REQUEST.toBuilder()
                .setPoints(0, RouterOuterClass.Point.newBuilder().setLat(38.0).setLon(-94.0).build()).build();
        StatusRuntimeException exception =
                assertThrows(StatusRuntimeException.class, () -> {routerStub.routePt(badPtRequest);});
        assertSame(exception.getStatus().getCode(), Status.NOT_FOUND.getCode());
    }

    // todo: uncomment this when fix is made so badly-timed PT requests fail fast
    /*
    @Test
    public void testBadTimeTransit() {
        RouterOuterClass.PtRouteRequest badPtRequest = PT_REQUEST.toBuilder()
                .setEarliestDepartureTime(Timestamp.newBuilder().setSeconds(100).build()).build();
        StatusRuntimeException exception =
                assertThrows(StatusRuntimeException.class, () -> {routerStub.routePt(badPtRequest);});
        assertSame(exception.getStatus().getCode(), Status.NOT_FOUND.getCode());
    }
    */
}
