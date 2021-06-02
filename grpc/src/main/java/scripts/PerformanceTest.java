package scripts;

import com.google.common.collect.Lists;
import com.google.protobuf.Timestamp;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import router.RouterOuterClass;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

public class PerformanceTest {
    private static final String[] OUTPUT_FILE_COLUMN_HEADERS = {"from", "to", "departure_time", "use_pareto", "duration", "num_transfers", "error"};
    private static final Logger logger = LoggerFactory.getLogger(PerformanceTest.class);

    public static void main(String[] args) throws IOException {
        String odFilePath = args[0];
        String outputFilePath = args[1];
        File outputFile = new File(outputFilePath);
        boolean usePareto = Boolean.parseBoolean(args[2]);
        String departureTime = args.length == 4 ? args[3] : "2019-10-13T18:00:00Z";

        logger.info("Reading input O/D pairs from file " + odFilePath + " with usePareto set to " + usePareto +
                ". Writing output to " + outputFilePath);
        List<RouterOuterClass.PtRouteRequest> requests = Files.lines(Paths.get(odFilePath))
                .skip(1)
                .map(line -> line.split(","))
                .map(line -> RouterOuterClass.PtRouteRequest.newBuilder()
                        .addPoints(0, RouterOuterClass.Point.newBuilder()
                                .setLat(Double.parseDouble(line[0]))
                                .setLon(Double.parseDouble(line[1]))
                                .build())
                        .addPoints(1, RouterOuterClass.Point.newBuilder()
                                .setLat(Double.parseDouble(line[2]))
                                .setLon(Double.parseDouble(line[3]))
                                .build())
                        .setEarliestDepartureTime(Timestamp.newBuilder()
                                .setSeconds(Instant.parse(departureTime).toEpochMilli() / 1000)
                                .build())
                        .setLimitSolutions(4)
                        .setMaxProfileDuration(10)
                        .setBetaWalkTime(1.5)
                        .setLimitStreetTimeSeconds(1440)
                        .setUsePareto(usePareto)
                        .setBetaTransfers(1440000)
                        .build())
                .collect(Collectors.toList());

        logger.info(requests.size() + " requests generated for performance testing");

        RouterClient client = new RouterClient("localhost", 50051);
        List<RouterPerformanceResult> results = Lists.newArrayList();
        int numComplete = 0;

        for (RouterOuterClass.PtRouteRequest request : requests) {
            logger.info("Running request number " + numComplete++);
            String from = request.getPoints(0).getLat() + "," + request.getPoints(0).getLon();
            String to = request.getPoints(1).getLat() + "," + request.getPoints(1).getLon();

            long startTime = System.nanoTime();
            try {
                RouterOuterClass.PtRouteReply reply = client.blockingStub.routePt(request);
                double executionTime = (System.nanoTime() - startTime) / 1000_000.0;
                List<Integer> numTransfers = reply.getPathsList().stream().map(path -> path.getPtLegsList().size() - 1).collect(Collectors.toList());
                results.add(new RouterPerformanceResult(from, to, departureTime, usePareto, executionTime, numTransfers, false));
            } catch (StatusRuntimeException e) {
                logger.warn("RPC failed: " + e.getMessage() + ";;;;;" + e.getStatus(), e.getStatus());
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

    private static class RouterClient {
        private router.RouterGrpc.RouterBlockingStub blockingStub;

        public RouterClient(String host, int port) {
            this(ManagedChannelBuilder.forAddress(host, port).usePlaintext());
        }

        public RouterClient(ManagedChannelBuilder<?> channelBuilder) {
            ManagedChannel channel = channelBuilder.build();
            this.blockingStub = router.RouterGrpc.newBlockingStub(channel);
        }
    }
}
