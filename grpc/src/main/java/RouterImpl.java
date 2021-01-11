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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.protobuf.Timestamp;
import com.google.rpc.Code;
import com.google.rpc.Status;
import com.graphhopper.*;
import com.graphhopper.gtfs.PtRouter;
import com.graphhopper.gtfs.Request;
import com.graphhopper.resources.PtRouteResource;
import com.graphhopper.routing.*;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.PMap;
import com.graphhopper.util.exceptions.PointNotFoundException;
import com.graphhopper.util.shapes.GHPoint;
import io.grpc.protobuf.StatusProto;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import router.RouterOuterClass.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.graphhopper.util.Parameters.Routing.INSTRUCTIONS;
import static java.util.stream.Collectors.toList;

public class RouterImpl extends router.RouterGrpc.RouterImplBase {

    private static final Logger logger = LoggerFactory.getLogger(RouterImpl.class);
    private final GraphHopper graphHopper;
    private final PtRouter ptRouter;
    private final MatrixAPI matrixAPI;
    private Map<String, String> gtfsLinkMappings;
    private Map<String, List<String>> gtfsRouteInfo;
    private Map<String, String> gtfsFeedIdMapping;
    // private final StatsDClient statsDClient;

    public RouterImpl(GraphHopper graphHopper, PtRouter ptRouter, MatrixAPI matrixAPI,
                      Map<String, String> gtfsLinkMappings,
                      Map<String, List<String>> gtfsRouteInfo,
                      Map<String, String> gtfsFeedIdMapping
                      /*StatsDClient statsDClient*/) {
        this.graphHopper = graphHopper;
        this.ptRouter = ptRouter;
        this.matrixAPI = matrixAPI;
        this.gtfsLinkMappings = gtfsLinkMappings;
        this.gtfsRouteInfo = gtfsRouteInfo;
        this.gtfsFeedIdMapping = gtfsFeedIdMapping;
        // this.statsDClient = statsDClient;
    }

    @Override
    public void routeStreetMode(StreetRouteRequest request, StreamObserver<StreetRouteReply> responseObserver) {
        GHRequest ghRequest = new GHRequest(
                request.getPointsList().stream().map(p -> new GHPoint(p.getLat(), p.getLon())).collect(Collectors.toList())
        );
        ghRequest.setProfile(request.getProfile());
        ghRequest.setLocale(Locale.US);
        ghRequest.setPathDetails(Lists.newArrayList("stable_edge_ids", "time"));

        PMap hints = new PMap();
        hints.putObject(INSTRUCTIONS, false);
        if (request.getAlternateRouteMaxPaths() > 1) {
            ghRequest.setAlgorithm("alternative_route");
            hints.putObject("alternative_route.max_paths", request.getAlternateRouteMaxPaths());
            hints.putObject("alternative_route.max_weight_factor", request.getAlternateRouteMaxWeightFactor());
            hints.putObject("alternative_route.max_share_factor", request.getAlternateRouteMaxShareFactor());
        }
        ghRequest.getHints().putAll(hints);

        try {
            GHResponse ghResponse = graphHopper.route(ghRequest);
            if (ghResponse.getAll().size() == 0) {
                Status status = Status.newBuilder()
                        .setCode(Code.NOT_FOUND.getNumber())
                        .setMessage("Path could not be found between "
                                + ghRequest.getPoints().get(0).lat + "," + ghRequest.getPoints().get(0).lon + " to "
                                + ghRequest.getPoints().get(1).lat + "," + ghRequest.getPoints().get(1).lon)
                        .build();
                responseObserver.onError(StatusProto.toStatusRuntimeException(status));
            } else {
                StreetRouteReply.Builder replyBuilder = StreetRouteReply.newBuilder();
                for (ResponsePath responsePath : ghResponse.getAll()) {
                    List<String> pathStableEdgeIds = responsePath.getPathDetails().get("stable_edge_ids").stream()
                            .map(pathDetail -> (String) pathDetail.getValue())
                            .collect(Collectors.toList());

                    List<Long> edgeTimes = responsePath.getPathDetails().get("time").stream()
                            .map(pathDetail -> (Long) pathDetail.getValue())
                            .collect(Collectors.toList());

                    replyBuilder.addPaths(StreetPath.newBuilder()
                            .setDurationMillis(responsePath.getTime())
                            .setDistanceMeters(responsePath.getDistance())
                            .addAllStableEdgeIds(pathStableEdgeIds)
                            .addAllEdgeDurationsMillis(edgeTimes)
                    );
                }

                /*
                String[] datadogTags = {"mode:" + request.getProfile(), "api:grpc"};
                statsDClient.incrementCounter("routers.num_requests", datadogTags);
                */

                responseObserver.onNext(replyBuilder.build());
                responseObserver.onCompleted();
            }
        } catch (Exception e) {
            Status status = Status.newBuilder()
                    .setCode(Code.INTERNAL.getNumber())
                    .setMessage("GH internal error! Path could not be found between "
                            + ghRequest.getPoints().get(0).lat + "," + ghRequest.getPoints().get(0).lon + " to "
                            + ghRequest.getPoints().get(1).lat + "," + ghRequest.getPoints().get(1).lon)
                    .build();
            responseObserver.onError(StatusProto.toStatusRuntimeException(status));
        }
    }

    // TODO: Clean up code based on fix-it comments in PR #26
    @Override
    public void routeMatrix(MatrixRouteRequest request, StreamObserver<MatrixRouteReply> responseObserver) {
        List<GHPoint> fromPoints = request.getFromPointsList().stream()
                .map(p -> new GHPoint(p.getLat(), p.getLon())).collect(toList());
        List<GHPoint> toPoints = request.getToPointsList().stream()
                .map(p -> new GHPoint(p.getLat(), p.getLon())).collect(toList());

        GHMRequest ghMatrixRequest = new GHMRequest();
        ghMatrixRequest.setFromPoints(fromPoints);
        ghMatrixRequest.setToPoints(toPoints);
        ghMatrixRequest.setOutArrays(new HashSet<>(request.getOutArraysList()));
        ghMatrixRequest.setProfile(request.getMode());
        ghMatrixRequest.setFailFast(request.getFailFast());

        try {
            GHMResponse ghMatrixResponse = matrixAPI.calc(ghMatrixRequest);
            /*
            String[] datadogTags = {"mode:" + request.getMode() + "_matrix", "api:grpc"};
            statsDClient.incrementCounter("routers.num_requests", datadogTags);
            */

            if (ghMatrixRequest.getFailFast() && ghMatrixResponse.hasInvalidPoints()) {
                MatrixErrors matrixErrors = new MatrixErrors();
                matrixErrors.addInvalidFromPoints(ghMatrixResponse.getInvalidFromPoints());
                matrixErrors.addInvalidToPoints(ghMatrixResponse.getInvalidToPoints());
                throw new MatrixCalculationException(matrixErrors);
            }
            int from_len = ghMatrixRequest.getFromPoints().size();
            int to_len = ghMatrixRequest.getToPoints().size();
            List<List<Long>> timeList = new ArrayList(from_len);
            List<Long> timeRow;
            List<List<Long>> distanceList = new ArrayList(from_len);
            List<Long> distanceRow;
            Iterator<MatrixElement> iter = ghMatrixResponse.getMatrixElementIterator();
            MatrixErrors matrixErrors = new MatrixErrors();
            StringBuilder debugBuilder = new StringBuilder();
            debugBuilder.append(ghMatrixResponse.getDebugInfo());

            for(int fromIndex = 0; fromIndex < from_len; ++fromIndex) {
                timeRow = new ArrayList(to_len);
                timeList.add(timeRow);
                distanceRow = new ArrayList(to_len);
                distanceList.add(distanceRow);

                for(int toIndex = 0; toIndex < to_len; ++toIndex) {
                    if (!iter.hasNext()) {
                        throw new IllegalStateException("Internal error, matrix dimensions should be " + from_len + "x" + to_len + ", but failed to retrieve element (" + fromIndex + ", " + toIndex + ")");
                    }

                    MatrixElement element = iter.next();
                    if (!element.isConnected()) {
                        matrixErrors.addDisconnectedPair(element.getFromIndex(), element.getToIndex());
                    }

                    if (ghMatrixRequest.getFailFast() && matrixErrors.hasDisconnectedPairs()) {
                        throw new MatrixCalculationException(matrixErrors);
                    }

                    long time = element.getTime();
                    timeRow.add(time == Long.MAX_VALUE ? null : Math.round((double)time / 1000.0D));

                    double distance = element.getDistance();
                    distanceRow.add(distance == Double.MAX_VALUE ? null : Math.round(distance));

                    debugBuilder.append(element.getDebugInfo());
                }
            }

            List<MatrixRow> timeRows = timeList.stream()
                    .map(row -> MatrixRow.newBuilder().addAllValues(row).build()).collect(toList());
            List<MatrixRow> distanceRows = distanceList.stream()
                    .map(row -> MatrixRow.newBuilder().addAllValues(row).build()).collect(toList());

            MatrixRouteReply result = MatrixRouteReply.newBuilder().addAllTimes(timeRows).addAllDistances(distanceRows).build();
            responseObserver.onNext(result);
            responseObserver.onCompleted();
        } catch (Exception e) {
            Status status = Status.newBuilder()
                    .setCode(Code.INTERNAL.getNumber())
                    .setMessage("GH internal error! Matrix request could not be completed.")
                    .build();
            responseObserver.onError(StatusProto.toStatusRuntimeException(status));
        }
    }

    @Override
    public void routePt(PtRouteRequest request, StreamObserver<PtRouteReply> responseObserver) {
        Point fromPoint = request.getPoints(0);
        Point toPoint = request.getPoints(1);

        Request ghPtRequest = new Request(fromPoint.getLat(), fromPoint.getLon(), toPoint.getLat(), toPoint.getLon());
        ghPtRequest.setEarliestDepartureTime(Instant.ofEpochSecond(
                request.getEarliestDepartureTime().getSeconds(), request.getEarliestDepartureTime().getNanos())
        );
        ghPtRequest.setLimitSolutions(request.getLimitSolutions());
        ghPtRequest.setLocale(Locale.US);
        ghPtRequest.setArriveBy(false);
        ghPtRequest.setPathDetails(Lists.newArrayList("stable_edge_ids"));

        try {
            GHResponse ghResponse = ptRouter.route(ghPtRequest);
            List<ResponsePath> pathsWithStableIds = Lists.newArrayList();
            for (ResponsePath path : ghResponse.getAll()) {
                // Ignore walking-only responses, because we route those separately from PT
                if (path.getLegs().size() == 1 && path.getLegs().get(0).type.equals("walk")) {
                    continue;
                }

                // Replace the path's legs with newly-constructed legs containing stable edge IDs
                ArrayList<Trip.Leg> legs = new ArrayList<>(path.getLegs());
                path.getLegs().clear();

                for (int i = 0; i < legs.size(); i++) {
                    Trip.Leg leg = legs.get(i);
                    if (leg instanceof Trip.WalkLeg) {
                        Trip.WalkLeg thisLeg = (Trip.WalkLeg) leg;
                        String travelSegmentType;
                        if (i == 0) {
                            travelSegmentType = "ACCESS";
                        } else if (i == legs.size() - 1) {
                            travelSegmentType = "EGRESS";
                        } else {
                            travelSegmentType = "TRANSFER"; // walk leg in middle of trip, not currently used by gh, reserved for future
                        }
                        path.getLegs().add(new PtRouteResource.CustomWalkLeg(thisLeg, fetchWalkLegStableIds(thisLeg), travelSegmentType));
                    } else if (leg instanceof Trip.PtLeg) {
                        Trip.PtLeg thisLeg = (Trip.PtLeg) leg;
                        path.getLegs().add(getCustomPtLeg(thisLeg));

                        // If this PT leg is followed by another PT leg, add a walk TRANSFER leg between them
                        if (i < legs.size() - 1 && legs.get(i + 1) instanceof Trip.PtLeg) {
                            Trip.PtLeg nextLeg = (Trip.PtLeg) legs.get(i + 1);
                            Trip.Stop lastStopOfThisLeg = thisLeg.stops.get(thisLeg.stops.size() - 1);
                            Trip.Stop firstStopOfNextLeg = nextLeg.stops.get(0);
                            if (!lastStopOfThisLeg.stop_id.equals(firstStopOfNextLeg.stop_id)) {
                                GHRequest r = new GHRequest(
                                        lastStopOfThisLeg.geometry.getY(), lastStopOfThisLeg.geometry.getX(),
                                        firstStopOfNextLeg.geometry.getY(), firstStopOfNextLeg.geometry.getX());
                                r.setProfile("foot");
                                r.setPathDetails(Arrays.asList("stable_edge_ids"));
                                GHResponse transfer = graphHopper.route(r);
                                if (!transfer.hasErrors()) {
                                    ResponsePath transferPath = transfer.getBest();
                                    Trip.WalkLeg transferLeg = new Trip.WalkLeg(
                                            lastStopOfThisLeg.stop_name,
                                            thisLeg.getArrivalTime(),
                                            transferPath.getPoints().getCachedLineString(false),
                                            transferPath.getDistance(),
                                            transferPath.getInstructions(),
                                            transferPath.getPathDetails(),
                                            Date.from(thisLeg.getArrivalTime().toInstant().plusMillis(transferPath.getTime()))
                                    );
                                    path.getLegs().add(new PtRouteResource.CustomWalkLeg(transferLeg, fetchWalkLegStableIds(transferLeg), "TRANSFER"));
                                }
                            }
                        }
                    }
                }

                // ACCESS legs contains stable IDs for both ACCESS and EGRESS legs for some reason,
                // so we remove the EGRESS leg IDs from the ACCESS leg before storing the path
                PtRouteResource.CustomWalkLeg accessLeg = (PtRouteResource.CustomWalkLeg) path.getLegs().get(0);
                PtRouteResource.CustomWalkLeg egressLeg = (PtRouteResource.CustomWalkLeg) path.getLegs().get(path.getLegs().size() - 1);
                accessLeg.stableEdgeIds.removeAll(egressLeg.stableEdgeIds);

                path.getPathDetails().clear();
                pathsWithStableIds.add(path);
            }

            if (pathsWithStableIds.size() == 0) {
                Status status = Status.newBuilder()
                        .setCode(Code.NOT_FOUND.getNumber())
                        .setMessage("Transit path could not be found between " + fromPoint.getLat() + "," +
                                fromPoint.getLon() + " to " + toPoint.getLat() + "," + toPoint.getLon())
                        .build();
                responseObserver.onError(StatusProto.toStatusRuntimeException(status));
            } else {
                PtRouteReply.Builder replyBuilder = PtRouteReply.newBuilder();
                for (ResponsePath responsePath : pathsWithStableIds) {
                    List<GenericLeg> legs = responsePath.getLegs().stream()
                            .map(leg -> createGenericLeg(leg))
                            .collect(toList());

                    replyBuilder.addPaths(PtPath.newBuilder()
                            .setDurationMillis(responsePath.getTime())
                            .setDistanceMeters(responsePath.getDistance())
                            .setTransfers(responsePath.getNumChanges())
                            .addAllLegs(legs)
                    );
                }

                /*
                String[] datadogTags = {"mode:pt", "api:grpc"};
                statsDClient.incrementCounter("routers.num_requests", datadogTags);
                */

                responseObserver.onNext(replyBuilder.build());
                responseObserver.onCompleted();
            }
        } catch (PointNotFoundException e) {
            Status status = Status.newBuilder()
                    .setCode(Code.NOT_FOUND.getNumber())
                    .setMessage("Path could not be found between " + fromPoint.getLat() + "," +
                            fromPoint.getLon() + " to " + toPoint.getLat() + "," + toPoint.getLon() +
                            "; one or both endpoints could not be snapped to a road segment")
                    .build();
            responseObserver.onError(StatusProto.toStatusRuntimeException(status));
        } catch (Exception e) {
            logger.error("ERRRRRRRROR! " + e.getMessage());
            Status status = Status.newBuilder()
                    .setCode(Code.INTERNAL.getNumber())
                    .setMessage("GH internal error! Path could not be found between " + fromPoint.getLat() + "," +
                            fromPoint.getLon() + " to " + toPoint.getLat() + "," + toPoint.getLon())
                    .build();
            responseObserver.onError(StatusProto.toStatusRuntimeException(status));
        }
    }

    private static List<String> fetchWalkLegStableIds(Trip.WalkLeg leg) {
        return leg.details.get("stable_edge_ids").stream()
                .map(idPathDetail -> (String) idPathDetail.getValue())
                .filter(id -> id.length() == 20)
                .collect(toList());
    }

    private static GenericLeg createGenericLeg(Trip.Leg leg) {
        if (leg.type.equals("walk")) {
            CustomWalkLeg walkLeg = (CustomWalkLeg) leg;
            FootLeg footLegProto = FootLeg.newBuilder()
                    .setDepartureTime(Timestamp.newBuilder()
                            .setSeconds(walkLeg.getDepartureTime().getTime() / 1000) // getTime() returns millis
                            .build())
                    .setArrivalTime(Timestamp.newBuilder()
                            .setSeconds(walkLeg.getArrivalTime().getTime() / 1000) // getTime() returns millis
                            .build())
                    .setDistanceMeters(walkLeg.getDistance())
                    .addAllStableEdgeIds(walkLeg.stableEdgeIds)
                    .setTravelSegmentType(walkLeg.travelSegmentType)
                    .build();
            return GenericLeg.newBuilder().setFootLeg(footLegProto).build();
        } else { // leg is a PT leg
            CustomPtLeg ptLeg = (CustomPtLeg) leg;
            PtLeg ptLegProto = PtLeg.newBuilder()
                    .setDepartureTime(Timestamp.newBuilder()
                            .setSeconds(ptLeg.getDepartureTime().getTime() / 1000) // getTime() returns millis
                            .build())
                    .setArrivalTime(Timestamp.newBuilder()
                            .setSeconds(ptLeg.getArrivalTime().getTime() / 1000) // getTime() returns millis
                            .build())
                    .setDistanceMeters(ptLeg.getDistance())
                    .addAllStableEdgeIds(ptLeg.stableEdgeIds)
                    .setTripId(ptLeg.trip_id)
                    .setRouteId(ptLeg.route_id)
                    .setAgencyName(ptLeg.agencyName)
                    .setRouteShortName(ptLeg.routeShortName)
                    .setRouteLongName(ptLeg.routeLongName)
                    .setRouteType(ptLeg.routeType)
                    .setDirection(ptLeg.trip_headsign)
                    .addAllStops(ptLeg.stops.stream().map(stop -> Stop.newBuilder()
                            .setStopId(stop.stop_id)
                            .setStopName(stop.stop_name)
                            .setArrivalTime(stop.arrivalTime == null ? Timestamp.newBuilder().build()
                                    : Timestamp.newBuilder().setSeconds(stop.arrivalTime.getTime() / 1000).build())
                            .setDepartureTime(stop.departureTime == null ? Timestamp.newBuilder().build()
                                    : Timestamp.newBuilder().setSeconds(stop.departureTime.getTime() / 1000).build())
                            .setPoint(Point.newBuilder().setLat(stop.geometry.getY()).setLon(stop.geometry.getX()).build())
                            .build()).collect(toList())
                    ).build();
            return GenericLeg.newBuilder().setPtLeg(ptLegProto).build();
        }
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

        public CustomPtLeg(Trip.PtLeg leg, List<String> stableEdgeIds, List<Trip.Stop> updatedStops, double distance,
                           String agencyName, String routeShortName, String routeLongName, String routeType) {
            super(leg.feed_id, leg.isInSameVehicleAsPrevious, leg.trip_id, leg.route_id,
                    leg.trip_headsign, updatedStops, distance, leg.travelTime, leg.geometry);
            this.stableEdgeIds = stableEdgeIds;
            this.agencyName = agencyName;
            this.routeShortName = routeShortName;
            this.routeLongName = routeLongName;
            this.routeType = routeType;
        }
    }

    private CustomPtLeg getCustomPtLeg(Trip.PtLeg leg) {
        List<Trip.Stop> stops = leg.stops;
        double legDistance = 0.0;

        // Retrieve stable edge IDs for each stop->stop segment of leg
        List<String> stableEdgeIdSegments = Lists.newArrayList();
        for (int i = 0; i < stops.size() - 1; i++) {
            Trip.Stop from = stops.get(i);
            Trip.Stop to = stops.get(i + 1);
            legDistance += DistanceCalcEarth.DIST_EARTH.calcDist(
                    from.geometry.getY(), from.geometry.getX(), to.geometry.getY(), to.geometry.getX()
            );

            String stopPair = from.stop_id + "," + to.stop_id;
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

        // Ordered list of GTFS route info, containing agency_name, route_short_name, route_long_name, route_type
        List<String> routeInfo = gtfsRouteInfo.getOrDefault(leg.route_id, Lists.newArrayList("", "", "", ""));

        if (!gtfsRouteInfo.containsKey(leg.route_id)) {
            logger.info("Failed to find route info for route " + leg.route_id + " for PT trip leg " + leg.toString());
        }

        // Add proper GTFS feed ID as prefix to all stop names in Leg
        List<Trip.Stop> updatedStops = Lists.newArrayList();
        for (Trip.Stop stop : leg.stops) {
            String updatedStopId = gtfsFeedIdMapping.get(leg.feed_id) + ":" + stop.stop_id;
            updatedStops.add(new Trip.Stop(updatedStopId, stop.stop_name, stop.geometry, stop.arrivalTime,
                    stop.plannedArrivalTime, stop.predictedArrivalTime, stop.arrivalCancelled, stop.departureTime,
                    stop.plannedDepartureTime, stop.predictedDepartureTime, stop.departureCancelled));
        }

        return new CustomPtLeg(leg, stableEdgeIdsList, updatedStops, legDistance,
                routeInfo.get(0), routeInfo.get(1), routeInfo.get(2), routeInfo.get(3));
    }
}
