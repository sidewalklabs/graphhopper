package scripts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.Lists;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.gtfs.*;
import com.graphhopper.http.GraphHopperManaged;
import com.graphhopper.jackson.GraphHopperConfigModule;
import com.graphhopper.jackson.Jackson;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class PerformanceTestJava {
    private static final String[] OUTPUT_FILE_COLUMN_HEADERS = {"from", "to", "departure_time", "use_pareto", "duration", "num_transfers", "error"};
    private static final Logger logger = LoggerFactory.getLogger(PerformanceTestJava.class);

    public static void main(String[] args) throws IOException {
        String odFilePath = args[0];
        String outputFilePath = args[1];
        File outputFile = new File(outputFilePath);
        boolean usePareto = Boolean.parseBoolean(args[2]);
        String configPath = args[3];
        String departureTime = args.length == 5 ? args[4] : "2019-10-13T18:00:00Z";

        logger.info("Reading input O/D pairs from file " + odFilePath + " with usePareto set to " + usePareto +
                ". Writing output to " + outputFilePath);

        // Start GH instance based on config given as command-line arg
        ObjectMapper yaml = Jackson.initObjectMapper(new ObjectMapper(new YAMLFactory()));
        yaml.registerModule(new GraphHopperConfigModule());
        JsonNode yamlNode = yaml.readTree(new File(configPath));
        GraphHopperConfig graphHopperConfiguration = yaml.convertValue(yamlNode.get("graphhopper"), GraphHopperConfig.class);
        ObjectMapper json = Jackson.newObjectMapper();
        GraphHopperManaged graphHopperManaged = new GraphHopperManaged(graphHopperConfiguration, json);
        graphHopperManaged.start();

        // Grab instance of PT router
        GraphHopper graphHopper = graphHopperManaged.getGraphHopper();
        final PtRouter ptRouter = new PtRouterImpl(
                graphHopper.getTranslationMap(), graphHopper.getGraphHopperStorage(),
                graphHopper.getLocationIndex(), ((GraphHopperGtfs) graphHopper).getGtfsStorage(),
                RealtimeFeed.empty(((GraphHopperGtfs) graphHopper).getGtfsStorage()),
                graphHopper.getPathDetailsBuilderFactory()
        );

        List<Request> requests = Files.lines(Paths.get(odFilePath))
                .skip(1)
                .map(line -> line.split(","))
                .map(line -> {
                    Request ghPtRequest = new Request(
                            Double.parseDouble(line[0]), Double.parseDouble(line[1]),
                            Double.parseDouble(line[2]), Double.parseDouble(line[3])
                    );
                    ghPtRequest.setEarliestDepartureTime(Instant.parse(departureTime));
                    ghPtRequest.setLimitSolutions(4);
                    ghPtRequest.setLocale(Locale.US);
                    ghPtRequest.setArriveBy(false);
                    ghPtRequest.setPathDetails(Lists.newArrayList("stable_edge_ids"));
                    ghPtRequest.setProfileQuery(true);
                    ghPtRequest.setMaxProfileDuration(Duration.ofMinutes(10));
                    ghPtRequest.setBetaWalkTime(1.5);
                    ghPtRequest.setLimitStreetTime(Duration.ofSeconds(1440));
                    ghPtRequest.setIgnoreTransfers(!usePareto); // ignoreTransfers=true means pareto queries are off
                    ghPtRequest.setBetaTransfers(1440000);
                    return ghPtRequest;
                }).collect(Collectors.toList());

        logger.info("Preparing to run " + requests.size() + " requests");

        List<RouterPerformanceResult> results = Lists.newArrayList();
        int numComplete = 0;

        for (Request request : requests) {
            logger.info("Running request number " + numComplete++);
            String from = request.getPoints().get(0).toString();
            String to = request.getPoints().get(1).toString();
            long startTime = System.nanoTime();
            try {
                GHResponse ghResponse = ptRouter.route(request);
                double executionTime = (System.nanoTime() - startTime) / 1000_000.0;
                List<Integer> numTransfers = ghResponse.getAll().stream().map(path -> (int) path.getLegs().stream().filter(leg -> leg.type.equals("pt")).count() - 1).collect(Collectors.toList());
                boolean error = false;
                if (ghResponse.getAll().size() == 0) error = true;
                results.add(new RouterPerformanceResult(from, to, departureTime, usePareto, executionTime, numTransfers, error));
            } catch (Exception e) {
                logger.warn("Request failed: " + e.getMessage());
                double executionTime = (System.nanoTime() - startTime) / 1000_000.0;
                results.add(new RouterPerformanceResult(from, to, departureTime, usePareto, executionTime, Lists.newArrayList(0), true));
            }
        }

        logger.info("Finished making requests! Writing results to CSV output file");
        try (CSVPrinter printer = new CSVPrinter(new FileWriter(outputFile), CSVFormat.DEFAULT.withHeader(OUTPUT_FILE_COLUMN_HEADERS))) {
            for (RouterPerformanceResult result : results) {
                printer.printRecord(result.from, result.to, result.departureTime, result.usePareto,
                        result.duration, result.numTransfers, result.error);
            }
        }
        logger.info("Done! CSV output file has been written at " + outputFilePath);
    }
}
