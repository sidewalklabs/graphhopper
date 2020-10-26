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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.graphhopper.*;
import com.graphhopper.gtfs.*;
import com.graphhopper.http.GraphHopperManaged;
import com.graphhopper.jackson.GraphHopperConfigModule;
import com.graphhopper.jackson.Jackson;
import com.graphhopper.util.PMap;
import com.graphhopper.util.shapes.GHPoint;
import io.grpc.stub.StreamObserver;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import router.RouterOuterClass.*;

import static com.graphhopper.util.Parameters.Routing.INSTRUCTIONS;
import static java.util.stream.Collectors.toList;

public class RouterImpl extends router.RouterGrpc.RouterImplBase {

    private static final Logger logger = LoggerFactory.getLogger(RouterImpl.class);
    private final GraphHopper graphHopper;
    private final PtRouter ptRouter;

    private static Map<String, String> gtfsLinkMappings;
    private static Map<String, String> gtfsRouteInfo;

    // Statically load GTFS link mapping and GTFS route info maps for use in building responses
    static {
        File linkMappingsDbFile = new File("transit_data/gtfs_link_mappings.db");
        if (linkMappingsDbFile.exists()) {
            DB db = DBMaker.newFileDB(linkMappingsDbFile).make();
            gtfsLinkMappings = db.getHashMap("gtfsLinkMappings");
            gtfsRouteInfo = db.getHashMap("gtfsRouteInfo");
            logger.info("Done loading GTFS link mappings and route info. Total number of mappings: " + gtfsLinkMappings.size());
        } else {
            logger.info("No GTFS link mapping mapdb file found! Skipped loading GTFS link mappings.");
        }
    }

    public RouterImpl(GraphHopper graphHopper, PtRouter ptRouter) {
        this.graphHopper = graphHopper;
        this.ptRouter = ptRouter;
    }

    @Override
    public void routeStreetMode(StreetRouteRequest request, StreamObserver<StreetRouteReply> responseObserver) {
        GHRequest ghRequest = new GHRequest(
                request.getPointsList().stream().map(p -> new GHPoint(p.getLat(), p.getLon())).collect(Collectors.toList())
        );
        ghRequest.setProfile(request.getProfile());
        ghRequest.setLocale(Locale.US);
        ghRequest.setPathDetails(Lists.newArrayList("stable_edge_ids"));

        PMap hints = new PMap();
        hints.putObject(INSTRUCTIONS, false);
        if (request.getAlgorithm().equals("alternative_route")) {
            ghRequest.setAlgorithm("alternative_route");
            hints.putObject("alternative_route.max_paths", request.getAlternateRouteMaxPaths());
            hints.putObject("alternative_route.max_weight_factor", request.getAlternateRouteMaxWeightFactor());
            hints.putObject("alternative_route.max_share_factor", request.getAlternateRouteMaxShareFactor());
        }
        ghRequest.getHints().putAll(hints);

        GHResponse ghResponse = graphHopper.route(ghRequest);

        StreetRouteReply.Builder replyBuilder = StreetRouteReply.newBuilder();
        for (ResponsePath responsePath : ghResponse.getAll()) {
            List<String> pathStableEdgeIds = responsePath.getPathDetails().get("stable_edge_ids").stream()
                    .map(pathDetail -> (String) pathDetail.getValue())
                    .collect(Collectors.toList());

            replyBuilder.addPaths(StreetPath.newBuilder()
                    .setTime(responsePath.getTime())
                    .setDistance(responsePath.getDistance())
                    .addAllStableEdgeIds(pathStableEdgeIds)
            );
        }
        responseObserver.onNext(replyBuilder.build());
        responseObserver.onCompleted();
    }

    /*
    public static void main(String[] args) throws IOException {
        testPtRouting();
    }

    public static void testPtRouting() throws IOException {
        // Start GH instance based on config given as command-line arg
        ObjectMapper yaml = Jackson.initObjectMapper(new ObjectMapper(new YAMLFactory()));
        yaml.registerModule(new GraphHopperConfigModule());
        JsonNode yamlNode = yaml.readTree(new File("default_gh_config.yaml"));
        GraphHopperConfig graphHopperConfiguration = yaml.convertValue(yamlNode.get("graphhopper"), GraphHopperConfig.class);
        ObjectMapper json = Jackson.newObjectMapper();
        GraphHopperManaged graphHopperManaged = new GraphHopperManaged(graphHopperConfiguration, json);
        graphHopperManaged.start();

        // Grab instances of auto/bike/ped router and PT router (if applicable)
        GraphHopper graphHopper = graphHopperManaged.getGraphHopper();
        PtRouter ptRouter = null;
        if (graphHopper instanceof GraphHopperGtfs) {
            ptRouter = new PtRouterImpl(graphHopper.getTranslationMap(), graphHopper.getGraphHopperStorage(), graphHopper.getLocationIndex(), ((GraphHopperGtfs) graphHopper).getGtfsStorage(), RealtimeFeed.empty(((GraphHopperGtfs) graphHopper).getGtfsStorage()), graphHopper.getPathDetailsBuilderFactory());
        }
        RouterImpl router = new RouterImpl(graphHopper, ptRouter);

        List<Point> points = Lists.newArrayList();
        points.add(Point.newBuilder().setLat(38.571091).setLon(-121.528625).build());
        points.add(Point.newBuilder().setLat(38.534526).setLon(-121.504914).build());

        PtRouteRequest r = PtRouteRequest.newBuilder()
                .setEarliestDepartureTime("2018-10-16T20:03:03.000Z")
                .addAllPoints(points)
                .setLimitSolutions(1)
                .build();

        router.routePt(r, new StreamObserver<PtRouteReply>() {
            @Override
            public void onNext(PtRouteReply ptRouteReply) {

            }

            @Override
            public void onError(Throwable throwable) {

            }

            @Override
            public void onCompleted() {

            }
        });
    }
    */

    @Override
    public void routePt(PtRouteRequest request, StreamObserver<PtRouteReply> responseObserver) {
        Point fromPoint = request.getPoints(0);
        Point toPoint = request.getPoints(1);

        Request ghPtRequest = new Request(fromPoint.getLat(), fromPoint.getLon(), toPoint.getLat(), toPoint.getLon());
        ghPtRequest.setEarliestDepartureTime(Instant.parse(request.getEarliestDepartureTime()));
        ghPtRequest.setLimitSolutions(request.getLimitSolutions());
        ghPtRequest.setLocale(Locale.US);
        ghPtRequest.setArriveBy(false);
        ghPtRequest.setPathDetails(Lists.newArrayList("stable_edge_ids"));

        GHResponse ghResponse = ptRouter.route(ghPtRequest);

        List<ResponsePath> pathsWithStableIds = Lists.newArrayList();
        for (ResponsePath path : ghResponse.getAll()) {
            // Ignore walking-only responses, because we route those separately from PT
            if (path.getLegs().size() == 1 && path.getLegs().get(0).type.equals("walk")) {
                continue;
            }

            // Add stable edge IDs to PT legs
            List<Trip.Leg> ptLegs = path.getLegs().stream()
                    .filter(leg -> leg.type.equals("pt"))
                    .map(leg -> getCustomPtLeg((Trip.PtLeg)leg))
                    .collect(toList());

            // Add stable edge IDs to walk legs
            List<Trip.Leg> walkLegs = path.getLegs().stream()
                    .filter(leg -> leg.type.equals("walk"))
                    .collect(toList());

            Trip.WalkLeg firstLeg = (Trip.WalkLeg) walkLegs.get(0);
            Trip.WalkLeg lastLeg = (Trip.WalkLeg) walkLegs.get(1);

            List<String> lastLegStableIds = lastLeg.details.get("stable_edge_ids").stream()
                    .map(idPathDetail -> (String) idPathDetail.getValue())
                    .filter(id -> id.length() == 20)
                    .collect(toList());

            // The first leg contains stable IDs for both walking legs for some reason,
            // so we remove the IDs from the last leg
            List<String> firstLegStableIds = firstLeg.details.get("stable_edge_ids").stream()
                    .map(idPathDetail -> (String) idPathDetail.getValue())
                    .filter(id -> id.length() == 20)
                    .collect(toList());
            firstLegStableIds.removeAll(lastLegStableIds);

            // Replace the path's legs with newly-constructed legs containing stable edge IDs
            path.getLegs().clear();
            path.getLegs().add(new CustomWalkLeg(firstLeg, firstLegStableIds, "ACCESS"));
            path.getLegs().addAll(ptLegs);
            path.getLegs().add(new CustomWalkLeg(lastLeg, lastLegStableIds, "EGRESS"));
            path.getPathDetails().clear();
            pathsWithStableIds.add(path);
        }

        PtRouteReply.Builder replyBuilder = PtRouteReply.newBuilder();
        for (ResponsePath responsePath : pathsWithStableIds) {
            List<FootLeg> footLegs = responsePath.getLegs().stream()
                    .filter(leg -> leg.type.equals("walk"))
                    .map(leg -> (CustomWalkLeg) leg)
                    .map(leg -> FootLeg.newBuilder()
                            .setDepartureTime(leg.getDepartureTime().getTime())
                            .setArrivalTime(leg.getArrivalTime().getTime())
                            .setDistance(leg.getDistance())
                            .addAllStableEdgeIds(leg.stableEdgeIds)
                            .setTravelSegmentType(leg.travelSegmentType)
                            .build())
                    .collect(toList());

            List<PtLeg> ptLegs = responsePath.getLegs().stream()
                    .filter(leg -> leg.type.equals("pt"))
                    .map(leg -> (CustomPtLeg) leg)
                    .map(leg -> PtLeg.newBuilder()
                            .setDepartureTime(leg.getDepartureTime().getTime())
                            .setArrivalTime(leg.getArrivalTime().getTime())
                            .setDistance(leg.getDistance())
                            .addAllStableEdgeIds(leg.stableEdgeIds)
                            .setTripId(leg.trip_id)
                            .setRouteId(leg.route_id)
                            .setAgencyName(leg.agencyName)
                            .setRouteShortName(leg.routeShortName)
                            .setRouteLongName(leg.routeLongName)
                            .setRouteType(leg.routeType)
                            .addAllStops(leg.stops.stream().map(stop -> Stop.newBuilder()
                                    .setStopId(stop.stop_id)
                                    .setStopName(stop.stop_name)
                                    .setArrivalTime(stop.arrivalTime == null ? 0 : stop.arrivalTime.getTime())
                                    .setDepartureTime(stop.departureTime == null ? 0 : stop.departureTime.getTime())
                                    .setPoint(Point.newBuilder().setLat(stop.geometry.getX()).setLon(stop.geometry.getY()).build())
                                    .build()).collect(toList())
                            ).build()
                    ).collect(toList());

            replyBuilder.addPaths(PtPath.newBuilder()
                    .setTime(responsePath.getTime())
                    .setDistance(responsePath.getDistance())
                    .setTransfers(responsePath.getNumChanges())
                    .addAllFootLegs(footLegs)
                    .addAllPtLegs(ptLegs)
            );
        }
        responseObserver.onNext(replyBuilder.build());
        responseObserver.onCompleted();
    }

    public static class CustomWalkLeg extends Trip.WalkLeg {
        public final List<String> stableEdgeIds;
        public final String type;
        public final String travelSegmentType;

        public CustomWalkLeg(Trip.WalkLeg leg, List<String> stableEdgeIds, String travelSegmentType) {
            super(leg.departureLocation, leg.getDepartureTime(), leg.geometry,
                    leg.distance, leg.instructions, leg.details, leg.getArrivalTime());
            this.stableEdgeIds = stableEdgeIds;
            this.details.clear();
            this.type = "foot";
            this.travelSegmentType = travelSegmentType;
        }
    }

    // Create new version of PtLeg class that stores stable edge IDs in class var;
    // this var will automatically get added to JSON response
    public static class CustomPtLeg extends Trip.PtLeg {
        public final List<String> stableEdgeIds;
        public final String agencyName;
        public final String routeShortName;
        public final String routeLongName;
        public final String routeType;

        public CustomPtLeg(Trip.PtLeg leg, List<String> stableEdgeIds, String agencyName, String routeShortName,
                           String routeLongName, String routeType) {
            super(leg.feed_id, leg.isInSameVehicleAsPrevious, leg.trip_id, leg.route_id,
                    leg.trip_headsign, leg.stops, leg.distance, leg.travelTime, leg.geometry);
            this.stableEdgeIds = stableEdgeIds;
            this.agencyName = agencyName;
            this.routeShortName = routeShortName;
            this.routeLongName = routeLongName;
            this.routeType = routeType;
        }
    }

    private CustomPtLeg getCustomPtLeg(Trip.PtLeg leg) {
        List<Trip.Stop> stops = leg.stops;

        // Retrieve stable edge IDs for each stop->stop segment of leg
        List<String> stableEdgeIdSegments = Lists.newArrayList();
        for (int i = 0; i < stops.size() - 1; i++) {
            String stopPair = stops.get(i).stop_id + "," + stops.get(i + 1).stop_id;
            if (gtfsLinkMappings.containsKey(stopPair)) {
                if (!gtfsLinkMappings.get(stopPair).isEmpty()) {
                    stableEdgeIdSegments.add(gtfsLinkMappings.get(stopPair));
                }
            }
        }

        List<String> stableEdgeIdsList = stableEdgeIdSegments.stream()
                .flatMap(segment -> Arrays.stream(segment.split(",")))
                .collect(toList());

        // Remove duplicates from stable ID list while retaining order;
        // needed because start/end of sequential segments overlap by 1 edge
        Set<String> stableEdgeIdsWithoutDuplicates = Sets.newLinkedHashSet(stableEdgeIdsList);
        stableEdgeIdsList.clear();
        stableEdgeIdsList.addAll(stableEdgeIdsWithoutDuplicates);

        // Split comma-separated GTFS route info string of agency_name,route_short_name,route_long_name,route_type
        String[] routeInfo = gtfsRouteInfo.containsKey(leg.route_id)
                ? gtfsRouteInfo.get(leg.route_id).split(",")
                : new String[]{"", "", "", ""};

        if (!gtfsRouteInfo.containsKey(leg.route_id)) {
            logger.info("Failed to find route info for route " + leg.route_id + " for PT trip leg " + leg.toString());
        }

        return new CustomPtLeg(leg, stableEdgeIdsList, routeInfo[0], routeInfo[1], routeInfo[2], routeInfo[3]);
    }
}