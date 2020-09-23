package com.graphhopper.replica;

import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.Route;
import com.conveyal.gtfs.model.Stop;
import com.conveyal.gtfs.model.StopTime;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.gtfs.GraphHopperGtfs;
import com.graphhopper.gtfs.GtfsStorage;
import com.graphhopper.util.details.PathDetail;
import org.apache.commons.lang3.tuple.Pair;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class GtfsLinkMapper {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final GraphHopper graphHopper;

    public GtfsLinkMapper(GraphHopper graphHopper) {
        this.graphHopper = graphHopper;
    }

    public void setGtfsLinkMappings() {
        logger.info("Starting GTFS link mapping process");
        GtfsStorage gtfsStorage = ((GraphHopperGtfs) graphHopper).getGtfsStorage();
        Map<String, GTFSFeed> gtfsFeedMap = gtfsStorage.getGtfsFeeds();

        // Define GTFS route types we care about linking to street edges: tram, bus, and cable car
        // Taken from Google's GTFS spec: https://developers.google.com/transit/gtfs/reference#routestxt
        final Set<Integer> STREET_BASED_ROUTE_TYPES = Sets.newHashSet(0, 3, 5);

        // Initialize mapdb database to store link mappings and route info
        logger.info("Initializing new mapdb file to store link mappings");
        DB db = DBMaker.newFileDB(new File("transit_data/gtfs_link_mappings.db")).make();
        HTreeMap<String, String> gtfsLinkMappings = db
                .createHashMap("gtfsLinkMappings")
                .keySerializer(Serializer.STRING)
                .valueSerializer(Serializer.STRING)
                .make();

        HTreeMap<String, String> gtfsRouteInfo = db
                .createHashMap("gtfsRouteInfo")
                .keySerializer(Serializer.STRING)
                .valueSerializer(Serializer.STRING)
                .make();

        // For testing
        // Set<String> allStableIds = Sets.newHashSet();

        // For each GTFS feed, pull out all stops for trips on GTFS routes that travel on the street network,
        // and then for each trip, route via car between each stop pair in sequential order, storing the returned IDs
        for (String feedId : gtfsFeedMap.keySet()) {
            GTFSFeed feed = gtfsFeedMap.get(feedId);
            logger.info("Processing GTFS feed " + feedId + " " + feed.feedId);

            // Store route information in db for _every_ route type
            Map<String, String> routeInfoMap = feed.routes.keySet().stream()
                    .map(routeId -> feed.routes.get(routeId))
                    .collect(Collectors.toMap(
                            route -> route.route_id,
                            route -> getRouteInfoString(route, feed.agency.get(route.agency_id).agency_name)
                    ));
            gtfsRouteInfo.putAll(routeInfoMap);

            // For mapping purposes, only look at routes for transit that use the street network
            Set<String> streetBasedRouteIdsForFeed = feed.routes.values().stream()
                    .filter(route -> STREET_BASED_ROUTE_TYPES.contains(route.route_type))
                    .map(route -> route.route_id)
                    .collect(Collectors.toSet());

            // Find all GTFS trips for each route
            Set<String> tripsForStreetBasedRoutes = feed.trips.values().stream()
                    .filter(trip -> streetBasedRouteIdsForFeed.contains(trip.route_id))
                    .map(trip -> trip.trip_id)
                    .collect(Collectors.toSet());

            // Find all stops for each trip
            SetMultimap<String, StopTime> tripIdToStopsInTrip = HashMultimap.create();
            feed.stop_times.values().stream()
                    .filter(stopTime -> tripsForStreetBasedRoutes.contains(stopTime.trip_id))
                    .forEach(stopTime -> tripIdToStopsInTrip.put(stopTime.trip_id, stopTime));

            Set<String> stopIdsForStreetBasedTrips = tripIdToStopsInTrip.values().stream()
                    .map(stopTime -> stopTime.stop_id)
                    .collect(Collectors.toSet());

            Map<String, Stop> stopsForStreetBasedTrips = feed.stops.values().stream()
                    .filter(stop -> stopIdsForStreetBasedTrips.contains(stop.stop_id))
                    .collect(Collectors.toMap(stop -> stop.stop_id, stop -> stop));

            logger.info("There are " + streetBasedRouteIdsForFeed.size() + " GTFS routes containing "
                    + tripsForStreetBasedRoutes.size() + " total trips to process for this feed. Routes to be computed for "
                    + stopIdsForStreetBasedTrips.size() + "/" + feed.stops.values().size() + " stop->stop pairs");

            int processedTripCount = 0;
            int odStopCount = 0;
            int nonUniqueODPairs = 0;
            int routeNotFoundCount = 0;
            // For each trip, route with auto between all O/D stop pairs,
            // and store returned stable edge IDs for each route in mapdb file
            for (String tripId : tripIdToStopsInTrip.keySet()) {
                if (processedTripCount % (tripIdToStopsInTrip.keySet().size() / 10) == 0) {
                    logger.info(processedTripCount + "/" + tripIdToStopsInTrip.keySet().size() + " trips for feed "
                            + feedId + " processed so far; " + nonUniqueODPairs + "/" + odStopCount
                            + " O/D stop pairs were non-unique, and were not routed between.");
                }

                // Fetch all sequentially-ordered stop->stop pairs for this trip
                List<Pair<Stop, Stop>> odStopsForTrip = getODStopsForTrip(tripIdToStopsInTrip.get(tripId), stopsForStreetBasedTrips);

                // Route a car between each stop->stop pair, and store the returned stable edge IDs in mapdb map
                for (Pair<Stop, Stop> odStopPair : odStopsForTrip) {
                    odStopCount++;

                    // Create String from the ID of each stop in pair, to use as key for map
                    String stopPairString = odStopPair.getLeft().stop_id + "," + odStopPair.getRight().stop_id;

                    // Don't route for any stop->stop pairs we've already routed between
                    if (gtfsLinkMappings.containsKey(stopPairString)) {
                        nonUniqueODPairs++;
                        continue;
                    }

                    // Form stop->stop auto routing requests and request a route
                    GHRequest odRequest = new GHRequest(
                            odStopPair.getLeft().stop_lat, odStopPair.getLeft().stop_lon,
                            odStopPair.getRight().stop_lat, odStopPair.getRight().stop_lon
                    );
                    odRequest.setProfile("car");
                    odRequest.setPathDetails(Lists.newArrayList("stable_edge_id"));
                    GHResponse response = graphHopper.route(odRequest);

                    // If stop->stop path couldn't be found by GH, don't store anything
                    if (response.getAll().size() == 0 || response.getAll().get(0).hasErrors()) {
                        routeNotFoundCount++;
                        continue;
                    }

                    // Parse stable IDs for each edge from response
                    List<PathDetail> responsePathEdgeIdDetails = response.getAll().get(0)
                            .getPathDetails().get("stable_edge_id");
                    List<String> pathEdgeIds = responsePathEdgeIdDetails.stream()
                            .map(pathDetail -> (String) pathDetail.getValue())
                            .collect(Collectors.toList());
                    // allStableIds.addAll(pathEdgeIds);

                    // Merge all path IDs into String to use as value for gtfs link map
                    String pathStableEdgeIdString = pathEdgeIds.stream().collect(Collectors.joining(","));
                    gtfsLinkMappings.put(stopPairString, pathStableEdgeIdString);
                }
                processedTripCount++;
            }
            logger.info("Done processing GTFS feed " + feedId + "; " + tripIdToStopsInTrip.keySet().size() +
                    " total trips processed; " + nonUniqueODPairs + "/" + odStopCount
                    + " O/D stop pairs were non-unique; routes for " + routeNotFoundCount + "/" + odStopCount
                    + " stop->stop pairs were not found");
        }
        db.commit();
        db.close();
        logger.info("Done creating GTFS link mappings for " + gtfsFeedMap.size() + " GTFS feeds");

        // For testing
        // logger.info("All stable edge IDs: ");
        // logger.info(allStableIds.stream().collect(Collectors.joining(",")));
    }

    // Given a set of StopTimes for a trip, and an overall mapping of stop IDs->Stop,
    // return a set of sequentially-ordered stop->stop pairs that make up the trip
    private List<Pair<Stop, Stop>> getODStopsForTrip(Set<StopTime> stopsInTrip, Map<String, Stop> allStops) {
        StopTime[] sortedStopsArray = new StopTime[stopsInTrip.size()];
        Arrays.sort(stopsInTrip.toArray(sortedStopsArray), (a, b) -> a.stop_sequence < b.stop_sequence ? -1 : 1);

        List<Pair<Stop, Stop>> odStopsForTrip = Lists.newArrayList();
        for (int i = 0; i < sortedStopsArray.length - 1; i++) {
            Stop startStop = allStops.get(sortedStopsArray[i].stop_id);
            Stop endStop = allStops.get(sortedStopsArray[i + 1].stop_id);
            odStopsForTrip.add(Pair.of(startStop, endStop));
        }
        return odStopsForTrip;
    }

    // Returns comma-separated string of agency_name,route_short_name,route_long_name,route_type
    private static String getRouteInfoString(Route route, String agencyName) {
        return agencyName + "," + route.route_short_name + "," + route.route_long_name + "," + route.route_type;
    }
}
