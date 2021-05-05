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
import io.dropwizard.Application;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.bundles.redirect.PathRedirect;
import io.dropwizard.bundles.redirect.RedirectBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionService;
import io.grpcweb.GrpcPortNumRelay;
import io.grpcweb.GrpcWebTrafficServlet;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class RouterServer {

    private static final Logger logger = LoggerFactory.getLogger(RouterServer.class);
    private Server server;
    private String configPath;
    private int numThreads;
    private int maxConnTime;
    private int maxConcCalls;
    private GraphHopperManaged graphHopperManaged;

    public RouterServer(String configPath, int numThreads, int maxConnTime, int maxConcCalls) {
        this.configPath = configPath;
        this.numThreads = numThreads;
        this.maxConnTime = maxConnTime;
        this.maxConcCalls = maxConcCalls;
    }

    private void start() throws Exception {
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
        int grpcPort = 50051;
        server = NettyServerBuilder.forPort(grpcPort)
                .addService(new RouterImpl(graphHopper, ptRouter, matrixAPI, gtfsLinkMappings, gtfsRouteInfo, gtfsFeedIdMapping))
                .addService(ProtoReflectionService.newInstance())
                .maxConnectionAge(maxConnTime, TimeUnit.SECONDS)
                .maxConnectionAgeGrace(30, TimeUnit.SECONDS)
                .maxConcurrentCallsPerConnection(maxConcCalls)
                .executor(Executors.newFixedThreadPool(numThreads))
                .build()
                .start();

        logger.info("Started server with max conn age of " + maxConnTime + " seconds, " + numThreads + " threads, " +
                "and " + maxConcCalls + " max concurrent calls per connection.");

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

        // Start the grpc-web proxy on grpc-web-port.
        new MyApplication().run("server", "config-proxy.yaml");
        logger.info("Started grpc-web proxy server");

        // grpc-web proxy needs to know the grpc-port# so it could connect to the grpc service.
        GrpcPortNumRelay.setGrpcPortNum(grpcPort);

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
     *
     * Required arg:
     * GH config file
     *
     * Optional args, specified with <arg name>=<arg value>:
     * number of server threads (arg name: num_threads; default: 3)
     * max connection time (arg name: max_conn_time; default: 10 seconds)
     * max concurrent calls per connection (arg name: max_conc_calls; default: 500)
     *
     * Example:
     * java -server -Xmx16g -Xms1g -XX:+UseG1GC -XX:MetaspaceSize=100M \
     * -classpath grpc/target/graphhopper-grpc-1.0-SNAPSHOT.jar  RouterServer default_gh_config.yaml \
     * max_conn_time=10 max_conn_calls=100
     *
     */
    public static void main(String[] args) throws Exception {
        if (!(args.length >= 1)) {
            throw new RuntimeException("Must include path to GH config in order to start server!");
        }
        String config = args[0];

        // Set defaults for other args
        int numThreads = 3;
        int maxConnTime = 30;
        int maxConcCalls = 500;

        // Parse any non-config args that were passed in
        for (int i = 1; i < args.length; i++) {
            String argName = args[i].split("=")[0];
            String argValue = args[i].split("=")[1];
            switch(argName) {
                case "num_threads":
                    numThreads = Integer.parseInt(argValue);
                    break;
                case "max_conc_calls":
                    maxConcCalls = Integer.parseInt(argValue);
                    break;
                case "max_conn_time":
                    maxConnTime = Integer.parseInt(argValue);
                    break;
                default:
                    logger.warn(argName + " was passed in, but is not recognized as a valid argument! Check for typos?");
            }

        }

        final RouterServer server = new RouterServer(config, numThreads, maxConnTime, maxConcCalls);
        server.start();
        server.blockUntilShutdown();
    }

    private static class MyApplication extends Application<MyConfiguration> {
        @Override
        public void initialize(Bootstrap bootstrap) {
            bootstrap.addBundle(new AssetsBundle("/assets/", "/maps/", "index.html", "maps"));
            bootstrap.addBundle(new AssetsBundle("/META-INF/resources/webjars", "/webjars", "index.html", "webjars"));
            bootstrap.addBundle(new RedirectBundle(new PathRedirect("/maps/pt", "/maps/pt/")));
        }

        @Override
        public void run(MyConfiguration configuration, Environment environment) throws Exception {
            environment.servlets().addServlet("grpc-web", GrpcWebTrafficServlet.class).addMapping("/api/*");
        }
    }
}
