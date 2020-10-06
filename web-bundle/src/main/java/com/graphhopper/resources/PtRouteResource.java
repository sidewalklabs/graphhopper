package com.graphhopper.resources;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.graphhopper.*;
import com.graphhopper.gtfs.GHLocation;
import com.graphhopper.gtfs.PtRouter;
import com.graphhopper.gtfs.Request;
import com.graphhopper.http.DurationParam;
import com.graphhopper.http.GHLocationParam;
import com.graphhopper.http.WebHelper;
import com.graphhopper.util.Helper;
import com.graphhopper.util.StopWatch;
import io.dropwizard.jersey.params.AbstractParam;
import io.dropwizard.jersey.params.InstantParam;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.ws.rs.*;
import java.io.File;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

@Path("route-pt")
public class PtRouteResource {
    private static final Logger logger = LoggerFactory.getLogger(PtRouteResource.class);
    private final GraphHopperAPI graphHopper;
    private final PtRouter ptRouter;
    private static Map<String, String> gtfsLinkMappings;
    private static Map<String, String> gtfsRouteInfo;

    // Statically load GTFS link mapping and GTFS route info maps for use in building responses
    static {
        DB db = DBMaker.newFileDB(new File("transit_data/gtfs_link_mappings.db")).make();
        gtfsLinkMappings = db.getHashMap("gtfsLinkMappings");
        gtfsRouteInfo = db.getHashMap("gtfsRouteInfo");
        logger.info("Done loading GTFS link mappings and route info. Total number of mappings: " + gtfsLinkMappings.size());
    }

    @Inject
    public PtRouteResource(GraphHopperAPI graphHopper, PtRouter ptRouter) {
        this.graphHopper = graphHopper;
        this.ptRouter = ptRouter;
    }

    @GET
    @Produces({"application/json"})
    public ObjectNode route(@QueryParam("point") @Size(min = 2,max = 2) List<GHLocationParam> requestPoints,
                            @QueryParam("pt.earliest_departure_time") @NotNull InstantParam departureTimeParam,
                            @QueryParam("pt.profile_duration") DurationParam profileDuration,
                            @QueryParam("pt.arrive_by") @DefaultValue("false") boolean arriveBy,
                            @QueryParam("locale") String localeStr,
                            @QueryParam("pt.ignore_transfers") Boolean ignoreTransfers,
                            @QueryParam("pt.profile") Boolean profileQuery,
                            @QueryParam("pt.limit_solutions") Integer limitSolutions,
                            @QueryParam("pt.limit_street_time") DurationParam limitStreetTime,
                            @QueryParam("details") List<String> pathDetails) {
        StopWatch stopWatch = (new StopWatch()).start();
        List<GHLocation> points = requestPoints.stream().map(AbstractParam::get).collect(Collectors.toList());
        Instant departureTime = departureTimeParam.get();
        Request request = new Request(points, departureTime);
        request.setArriveBy(arriveBy);
        Optional.ofNullable(profileQuery).ifPresent(request::setProfileQuery);
        Optional.ofNullable(profileDuration.get()).ifPresent(request::setMaxProfileDuration);
        Optional.ofNullable(ignoreTransfers).ifPresent(request::setIgnoreTransfers);
        Optional.ofNullable(localeStr).ifPresent((s) -> {request.setLocale(Helper.getLocale(s));});
        Optional.ofNullable(limitSolutions).ifPresent(request::setLimitSolutions);
        Optional.ofNullable(limitStreetTime.get()).ifPresent(request::setLimitStreetTime);
        Optional.ofNullable(pathDetails).ifPresent(request::setPathDetails);

        GHResponse route = this.ptRouter.route(request);

        List<ResponsePath> pathsWithStableIds = Lists.newArrayList();
        for (ResponsePath path : route.getAll()) {
            // Ignore walking-only responses, because we route those separately from PT
            if (path.getLegs().size() == 1 && path.getLegs().get(0).type.equals("walk")) {
                continue;
            }

            ArrayList<Trip.Leg> legs = new ArrayList<>(path.getLegs());

            // Replace the path's legs with newly-constructed legs containing stable edge IDs
            path.getLegs().clear();

            for (int i = 0; i < legs.size(); i++) {
                Trip.Leg leg = legs.get(i);
                if (leg instanceof Trip.WalkLeg) {
                    String travelSegmentType;
                    if (i==0)
                        travelSegmentType = "ACCESS";
                    else if (i == legs.size()-1)
                        travelSegmentType = "EGRESS";
                    else
                        travelSegmentType = "TRANSFER"; // walk leg in middle of trip, not currently used by gh, reserved for future
                    path.getLegs().add(new CustomWalkLeg(((Trip.WalkLeg) leg), stableIds((Trip.WalkLeg) leg), travelSegmentType));
                } else if (leg instanceof Trip.PtLeg) {
                    Trip.PtLeg thisLeg = (Trip.PtLeg) leg;
                    path.getLegs().add(getCustomPtLeg(thisLeg));
                    if (i < legs.size() - 1 && legs.get(i+1) instanceof Trip.PtLeg) {
                        Trip.PtLeg nextLeg = (Trip.PtLeg) legs.get(i + 1);
                        Trip.Stop lastStopOfThisLeg = thisLeg.stops.get(thisLeg.stops.size() - 1);
                        Trip.Stop firstStopOfNextLeg = nextLeg.stops.get(0);
                        if (!lastStopOfThisLeg.stop_id.equals(firstStopOfNextLeg.stop_id)) {
                            GHRequest r = new GHRequest(
                                    lastStopOfThisLeg.geometry.getY(), lastStopOfThisLeg.geometry.getX(),
                                    firstStopOfNextLeg.geometry.getY(), firstStopOfNextLeg.geometry.getX());
                            r.setProfile("foot");
                            r.setPathDetails(Arrays.asList("r5_edge_id"));
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
                                path.getLegs().add(new CustomWalkLeg(transferLeg, stableIds(transferLeg), "TRANSFER"));
                            }
                        }
                    }
                }
            }
            path.getPathDetails().clear();
            pathsWithStableIds.add(path);
        }

        GHResponse routeWithStableIds = new GHResponse();
        routeWithStableIds.addDebugInfo(route.getDebugInfo());
        routeWithStableIds.addErrors(route.getErrors());
        pathsWithStableIds.forEach(path -> routeWithStableIds.add(path));

        return WebHelper.jsonObject(routeWithStableIds, true, true, false, false, stopWatch.stop().getMillis());
    }

    private List<String> stableIds(Trip.WalkLeg firstLeg) {
        return firstLeg.details.get("r5_edge_id").stream()
                .map(idPathDetail -> (String) idPathDetail.getValue())
                .filter(id -> id.length() == 20)
                .collect(toList());
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
