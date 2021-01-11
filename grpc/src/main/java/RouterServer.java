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
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.gtfs.GraphHopperGtfs;
import com.graphhopper.gtfs.PtRouter;
import com.graphhopper.gtfs.PtRouterImpl;
import com.graphhopper.gtfs.RealtimeFeed;
import com.graphhopper.http.GraphHopperManaged;
import com.graphhopper.jackson.GraphHopperConfigModule;
import com.graphhopper.jackson.Jackson;
import com.graphhopper.routing.GHMatrixAPI;
import com.graphhopper.routing.MatrixAPI;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionService;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class RouterServer {

    private static final Logger logger = LoggerFactory.getLogger(RouterServer.class);
    private Server server;
    private String configPath;
    private GraphHopperManaged graphHopperManaged;

    public RouterServer(String configPath) {
        this.configPath = configPath;
    }

    private void start() throws IOException {
        // Start GH instance based on config given as command-line arg
        ObjectMapper yaml = Jackson.initObjectMapper(new ObjectMapper(new YAMLFactory()));
        yaml.registerModule(new GraphHopperConfigModule());
        JsonNode yamlNode = yaml.readTree(new File(configPath));
        GraphHopperConfig graphHopperConfiguration = yaml.convertValue(yamlNode.get("graphhopper"), GraphHopperConfig.class);
        ObjectMapper json = Jackson.newObjectMapper();
        graphHopperManaged = new GraphHopperManaged(graphHopperConfiguration, json);
        graphHopperManaged.start();

        // Grab instances of auto/bike/ped router and PT router (if applicable)
        GraphHopper graphHopper = graphHopperManaged.getGraphHopper();
        PtRouter ptRouter = null;
        if (graphHopper instanceof GraphHopperGtfs) {
            ptRouter = new PtRouterImpl(graphHopper.getTranslationMap(), graphHopper.getGraphHopperStorage(), graphHopper.getLocationIndex(), ((GraphHopperGtfs) graphHopper).getGtfsStorage(), RealtimeFeed.empty(((GraphHopperGtfs) graphHopper).getGtfsStorage()), graphHopper.getPathDetailsBuilderFactory());
        }

        // Create matrix API instance
        MatrixAPI matrixAPI = new GHMatrixAPI(graphHopper, graphHopperConfiguration);

        // Load GTFS link mapping and GTFS info maps for use in building responses
        Map<String, String> gtfsLinkMappings = null;
        Map<String, List<String>> gtfsRouteInfo = null;
        Map<String, String> gtfsFeedIdMapping = null;

        File linkMappingsDbFile = new File("transit_data/gtfs_link_mappings.db");
        if (linkMappingsDbFile.exists()) {
            DB db = DBMaker.newFileDB(linkMappingsDbFile).make();
            gtfsLinkMappings = db.getHashMap("gtfsLinkMappings");
            gtfsRouteInfo = db.getHashMap("gtfsRouteInfo");
            gtfsFeedIdMapping = db.getHashMap("gtfsFeedIdMap");
            logger.info("Done loading GTFS link mappings and route info. Total number of mappings: " + gtfsLinkMappings.size());
        } else {
            logger.info("No GTFS link mapping mapdb file found! Skipped loading GTFS link mappings.");
        }

        /*
        // Initialize Datadog client
        StatsDClient statsDClient = new NonBlockingStatsDClientBuilder()
                .hostname(System.getenv("DD_AGENT_HOST"))
                .port(8125)
                .build();

        logger.info("Datadog agent host IP is: " + System.getenv("DD_AGENT_HOST"));
        */

        // Start server
        server = NettyServerBuilder.forPort(50051)
                .addService(new RouterImpl(graphHopper, ptRouter, matrixAPI, gtfsLinkMappings, gtfsRouteInfo, gtfsFeedIdMapping))
                .addService(ProtoReflectionService.newInstance())
                .maxConnectionAge(10, TimeUnit.SECONDS)
                .maxConnectionAgeGrace(10, TimeUnit.SECONDS)
                .executor(Executors.newFixedThreadPool(16))
                .build()
                .start();

        logger.info("Started server with max conn age of 10 seconds");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("*** shutting down gRPC server since JVM is shutting down");
            try {
                graphHopperManaged.stop();
                RouterServer.this.stop();
            } catch (InterruptedException e) {
                e.printStackTrace(System.err);
            }
            System.err.println("*** server shut down");
        }));
    }

    private void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon threads.
     */
    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    /**
     * Main launches the server from the command line.
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != 1) {
            throw new RuntimeException("Must include path to GH config in order to start server!");
        }
        final RouterServer server = new RouterServer(args[0]);
        server.start();
        server.blockUntilShutdown();
    }
}
