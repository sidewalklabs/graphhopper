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

package com.graphhopper.http;

import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.Route;
import com.conveyal.gtfs.model.Stop;
import com.conveyal.gtfs.model.StopTime;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.*;
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.config.Profile;
import com.graphhopper.jackson.Jackson;
import com.graphhopper.json.geo.JsonFeatureCollection;
import com.graphhopper.reader.gtfs.CustomGraphHopperGtfs;
import com.graphhopper.reader.gtfs.GraphHopperGtfs;
import com.graphhopper.reader.gtfs.GtfsStorage;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.replica.CustomCarFlagEncoder;
import com.graphhopper.routing.ee.vehicles.TruckFlagEncoder;
import com.graphhopper.routing.lm.LandmarkStorage;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.util.spatialrules.SpatialRuleLookupHelper;
import com.graphhopper.routing.weighting.custom.CustomProfile;
import com.graphhopper.routing.weighting.custom.CustomWeighting;
import com.graphhopper.stableid.EncodedValueFactoryWithStableId;
import com.graphhopper.stableid.PathDetailsBuilderFactoryWithEdgeKey;
import com.graphhopper.stableid.StableIdEncodedValues;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.details.PathDetail;
import com.graphhopper.util.shapes.BBox;
import io.dropwizard.lifecycle.Managed;
import org.apache.commons.lang3.tuple.Pair;
import org.locationtech.jts.geom.Envelope;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static com.graphhopper.util.Helper.UTF_CS;

public class GraphHopperManaged implements Managed {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final GraphHopper graphHopper;

    public GraphHopperManaged(GraphHopperConfig configuration, ObjectMapper objectMapper) {
        ObjectMapper localObjectMapper = objectMapper.copy();
        localObjectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        String splitAreaLocation = configuration.getString(Parameters.Landmark.PREPARE + "split_area_location", "");
        JsonFeatureCollection landmarkSplittingFeatureCollection;
        try (Reader reader = splitAreaLocation.isEmpty() ? new InputStreamReader(LandmarkStorage.class.getResource("map.geo.json").openStream(), UTF_CS) : new InputStreamReader(new FileInputStream(splitAreaLocation), UTF_CS)) {
            landmarkSplittingFeatureCollection = localObjectMapper.readValue(reader, JsonFeatureCollection.class);
        } catch (IOException e1) {
            logger.error("Problem while reading border map GeoJSON. Skipping this.", e1);
            landmarkSplittingFeatureCollection = null;
        }
        if (configuration.has("gtfs.file")) {
            graphHopper = new CustomGraphHopperGtfs(configuration);
        } else {
            graphHopper = new GraphHopperOSM(landmarkSplittingFeatureCollection) {
                @Override
                protected void registerCustomEncodedValues(EncodingManager.Builder emBuilder) {
                    StableIdEncodedValues.createAndAddEncodedValues(emBuilder);
                }
            }.forServer();
        }
        if (!configuration.getString("spatial_rules.location", "").isEmpty()) {
            throw new RuntimeException("spatial_rules.location has been deprecated. Please use spatial_rules.borders_directory instead.");
        }
        String spatialRuleBordersDirLocation = configuration.getString("spatial_rules.borders_directory", "");
        if (!spatialRuleBordersDirLocation.isEmpty()) {
            final BBox maxBounds = BBox.parseBBoxString(configuration.getString("spatial_rules.max_bbox", "-180, 180, -90, 90"));
            final Path bordersDirectory = Paths.get(spatialRuleBordersDirLocation);
            List<JsonFeatureCollection> jsonFeatureCollections = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(bordersDirectory, "*.{geojson,json}")) {
                for (Path borderFile : stream) {
                    try (BufferedReader reader = Files.newBufferedReader(borderFile, StandardCharsets.UTF_8)) {
                        JsonFeatureCollection jsonFeatureCollection = localObjectMapper.readValue(reader, JsonFeatureCollection.class);
                        jsonFeatureCollections.add(jsonFeatureCollection);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            SpatialRuleLookupHelper.buildAndInjectCountrySpatialRules(graphHopper,
                    new Envelope(maxBounds.minLon, maxBounds.maxLon, maxBounds.minLat, maxBounds.maxLat), jsonFeatureCollections);
        }

        ObjectMapper yamlOM = Jackson.initObjectMapper(new ObjectMapper(new YAMLFactory()));
        ObjectMapper jsonOM = Jackson.newObjectMapper();
        List<Profile> newProfiles = new ArrayList<>();
        for (Profile profile : configuration.getProfiles()) {
            if (!CustomWeighting.NAME.equals(profile.getWeighting())) {
                newProfiles.add(profile);
                continue;
            }
            String customModelLocation = profile.getHints().getString("custom_model_file", "");
            if (customModelLocation.isEmpty())
                throw new IllegalArgumentException("Missing 'custom_model_file' field in profile '" + profile.getName() + "' if you want an empty custom model set it to 'empty'");
            if ("empty".equals(customModelLocation))
                newProfiles.add(new CustomProfile(profile).setCustomModel(new CustomModel()));
            else
                try {
                    CustomModel customModel = (customModelLocation.endsWith(".json") ? jsonOM : yamlOM).readValue(new File(customModelLocation), CustomModel.class);
                    newProfiles.add(new CustomProfile(profile).setCustomModel(customModel));
                } catch (Exception ex) {
                    throw new RuntimeException("Cannot load custom_model from " + customModelLocation + " for profile " + profile.getName(), ex);
                }
        }
        configuration.setProfiles(newProfiles);

        graphHopper.setFlagEncoderFactory(new FlagEncoderFactory() {
            private FlagEncoderFactory delegate = new DefaultFlagEncoderFactory();

            @Override
            public FlagEncoder createFlagEncoder(String name, PMap configuration) {
                if (name.startsWith("car")) {
                    return new CustomCarFlagEncoder(configuration, name);
                } else if (name.equals("truck")) {
                    return TruckFlagEncoder.createTruck(configuration, "truck");
                } else {
                    return delegate.createFlagEncoder(name, configuration);
                }
            }
        });
        graphHopper.setEncodedValueFactory(new EncodedValueFactoryWithStableId());
        graphHopper.init(configuration);
        graphHopper.setPathDetailsBuilderFactory(new PathDetailsBuilderFactoryWithEdgeKey());
    }

    @Override
    public void start() {
        graphHopper.importOrLoad();
        logger.info("loaded graph at:{}, data_reader_file:{}, encoded values:{}, {}",
                graphHopper.getGraphHopperLocation(), graphHopper.getDataReaderFile(),
                graphHopper.getEncodingManager().toEncodedValuesAsString(),
                graphHopper.getGraphHopperStorage().toDetailsString());
    }

    public GraphHopper getGraphHopper() {
        return graphHopper;
    }

    @Override
    public void stop() {
        graphHopper.close();
    }

    public void setStableEdgeIds() {
        GraphHopperStorage graphHopperStorage = graphHopper.getGraphHopperStorage();
        AllEdgesIterator edgesIterator = graphHopperStorage.getAllEdges();
        NodeAccess nodes = graphHopperStorage.getNodeAccess();
        EncodingManager encodingManager = graphHopper.getEncodingManager();
        StableIdEncodedValues stableIdEncodedValues = StableIdEncodedValues.fromEncodingManager(encodingManager);

        // Set both forward and reverse stable edge IDs for each edge
        int assignedIdCount = 0;
        while (edgesIterator.next()) {
            // Ignore setting stable IDs for transit edges, which have a distance of 0
            if (edgesIterator.getDistance() != 0) {
                stableIdEncodedValues.setStableId(true, edgesIterator, nodes);
                stableIdEncodedValues.setStableId(false, edgesIterator, nodes);
                assignedIdCount++;
            }
        }
        graphHopperStorage.flush();
        logger.info("Total number of bidirectional edges assigned with stable edge IDs: " + assignedIdCount);
    }

    public void setGtfsLinkMappings() {
        logger.info("Starting GTFS link mapping process");
        GtfsStorage gtfsStorage = ((GraphHopperGtfs) graphHopper).getGtfsStorage();
        Map<String, GTFSFeed> gtfsFeedMap = gtfsStorage.getGtfsFeeds();
        final Set<Integer> streetRouteTypes = Sets.newHashSet(Route.BUS, Route.TRAM, Route.CABLE_CAR);

        // Initialize mapdb database to store link mappings
        logger.info("Initializing new mapdb file to store link mappings");
        DB db = DBMaker.newFileDB(new File("transit_data/gtfs_link_mappings.db")).make();
        HTreeMap<String, String> gtfsLinkMappings = db
                .createHashMap("gtfsLinkMappings")
                .keySerializer(Serializer.STRING)
                .valueSerializer(Serializer.STRING)
                .make();

        // For each GTFS feed, pull out all stops for trips on GTFS routes that travel on the street network,
        // and then for each trip, route via car between each stop pair in sequential order, storing the returned IDs
        for (String feedId : gtfsFeedMap.keySet()) {
            GTFSFeed feed = gtfsFeedMap.get(feedId);
            logger.info("Processing GTFS feed " + feedId);

            // Only look at routes for transit types that travel on the street network
            Set<String> validRouteIdsForFeed = feed.routes.values().stream()
                    .filter(route -> streetRouteTypes.contains(route.route_type))
                    .map(route -> route.route_id)
                    .collect(Collectors.toSet());

            // Find all GTFS trips for each route
            Set<String> tripsForValidRoutes = feed.trips.values().stream()
                    .filter(trip -> validRouteIdsForFeed.contains(trip.route_id))
                    .map(trip -> trip.trip_id)
                    .collect(Collectors.toSet());

            // Find all stops for each trip
            SetMultimap<String, StopTime> tripIdToStopsInTrip = HashMultimap.create();
            feed.stop_times.values().stream()
                    .filter(stopTime -> tripsForValidRoutes.contains(stopTime.trip_id))
                    .forEach(stopTime -> tripIdToStopsInTrip.put(stopTime.trip_id, stopTime));

            Set<String> stopIdsForAllTrips = tripIdToStopsInTrip.values().stream()
                    .map(stopTime -> stopTime.stop_id)
                    .collect(Collectors.toSet());

            Map<String, Stop> stopsForAllTrips = feed.stops.values().stream()
                    .filter(stop -> stopIdsForAllTrips.contains(stop.stop_id))
                    .collect(Collectors.toMap(stop -> stop.stop_id, stop -> stop));

            logger.info("There are " + validRouteIdsForFeed.size() + " GTFS routes containing "
                    + tripsForValidRoutes.size() + " total trips to process for this feed. Routes to be computed for "
                    + stopIdsForAllTrips.size() + "/" + feed.stops.values().size() + " stop->stop pairs");

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
                Set<Pair<Stop, Stop>> odStopsForTrip = getODStopsForTrip(tripIdToStopsInTrip.get(tripId), stopsForAllTrips);

                // Route a car between each stop->stop pair, and store the returned stable edge IDs in in-mem map
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
                    odRequest.setPathDetails(Lists.newArrayList("r5_edge_id"));
                    GHResponse response = graphHopper.route(odRequest);
                    assert(response.getAll().size() == 1);

                    // Parse all stable edge IDs from response, and merge into String to use as value for map
                    List<PathDetail> responsePathDetails = response.getAll().get(0).getPathDetails().get("r5_edge_id");

                    // If stop->stop path couldn't be found by GH, don't store anything
                    if (responsePathDetails == null) {
                        routeNotFoundCount++;
                        continue;
                    }
                    List<String> pathStableEdgeIds = responsePathDetails.stream()
                            .map(pathDetail -> (String) pathDetail.getValue())
                            .collect(Collectors.toList());
                    String pathStableEdgeIdString = pathStableEdgeIds.stream().collect(Collectors.joining(","));

                    // Add entry to in-memory map
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
    }

    // Given a set of StopTimes for a trip, and an overall mapping of stop IDs->Stop,
    // return a set of sequentially-ordered stop->stop pairs that make up the trip
    private Set<Pair<Stop, Stop>> getODStopsForTrip(Set<StopTime> stopsInTrip, Map<String, Stop> allStops) {
        StopTime[] sortedStopsArray = new StopTime[stopsInTrip.size()];
        Arrays.sort(stopsInTrip.toArray(sortedStopsArray), (a, b) -> a.stop_sequence < b.stop_sequence ? -1 : 1);

        Set<Pair<Stop, Stop>> odStopsForTrip = Sets.newHashSet();
        for (int i = 0; i < sortedStopsArray.length - 1; i++) {
            Stop startStop = allStops.get(sortedStopsArray[i].stop_id);
            Stop endStop = allStops.get(sortedStopsArray[i + 1].stop_id);
            odStopsForTrip.add(Pair.of(startStop, endStop));
        }
        return odStopsForTrip;
    }
}
