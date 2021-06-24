package com.replica;

import com.google.common.base.Preconditions;
import com.graphhopper.GraphHopper;
import com.graphhopper.config.Profile;
import com.graphhopper.http.GraphHopperApplication;
import com.graphhopper.http.GraphHopperBundle;
import com.graphhopper.http.GraphHopperManaged;
import com.graphhopper.http.GraphHopperServerConfiguration;
import com.graphhopper.http.cli.ExportCommand;
import com.graphhopper.http.cli.ImportCommand;
import com.graphhopper.replica.StreetEdgeExportRecord;
import com.graphhopper.replica.StreetEdgeExporter;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.Helper;
import io.dropwizard.cli.Cli;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.util.JarLocation;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;

import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mapdb.DB;
import org.mapdb.DBMaker;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class StreetEdgeExporterTest {

    private static final String TARGET_DIR = "./target/gtfs-app-gh/";
    private static final String TRANSIT_DATA_DIR = "transit_data/";

    private Bootstrap<GraphHopperServerConfiguration> bootstrap;
    private Cli cli;

    @BeforeAll
    public void setUp() {
        // Fresh target directory
        Helper.removeDir(new File(TARGET_DIR));
        // Create new empty directory for GTFS/OSM resources
        File transitDataDir = new File(TRANSIT_DATA_DIR);
        if (transitDataDir.exists()) {
            throw new IllegalStateException(TRANSIT_DATA_DIR + " directory should not already exist.");
        }
        Preconditions.checkState(transitDataDir.mkdir(), "could not create directory " + TRANSIT_DATA_DIR);

        // Setup necessary mock
        final JarLocation location = mock(JarLocation.class);
        when(location.getVersion()).thenReturn(Optional.of("1.0.0"));

        // Add commands you want to test
        bootstrap = new Bootstrap<>(new GraphHopperApplication());
        bootstrap.addBundle(new GraphHopperBundle());
        bootstrap.addCommand(new ImportCommand());
        bootstrap.addCommand(new ExportCommand());

        // Build what'll run the command and interpret arguments
        cli = new Cli(location, bootstrap, System.out, System.err);
        cli.run("import", "test-data/beatty-sample-feed-config-car.yml");
    }

    @AfterAll
    public static void cleanUp() {
        Helper.removeDir(new File(TARGET_DIR));
        Helper.removeDir(new File(TRANSIT_DATA_DIR));
    }

    @Test
    public void testExportEndToEnd() throws IOException {
        cli.run("export", "test-data/beatty-sample-feed-config-car.yml");
        CSVFormat format = StreetEdgeExporter.CSV_FORMAT;
        File expectedOutputLocation = new File(TARGET_DIR + "street_edges.csv");
        CSVParser parser = CSVParser.parse(expectedOutputLocation, StandardCharsets.UTF_8, format);
        List<CSVRecord> records = parser.getRecords();
        assertEquals(769, records.size());
    }

    @Test
    public void testExportSingleRecord() {
        GraphHopperServerConfiguration config = new GraphHopperServerConfiguration();
        config.getGraphHopperConfiguration().
                putObject("graph.flag_encoders", "car,foot").
                putObject("datareader.file", "test-data/beatty.osm").
                putObject("gtfs.file", "test-data/sample-feed.zip").
                putObject("graph.location", TARGET_DIR).
                setProfiles(Collections.singletonList(new Profile("foot").setVehicle("foot").setWeighting("fastest")));

        // TODO copied from ExportCommand
        // Read in pre-built GH graph files from /transit_data/graphhopper
        final GraphHopperManaged graphHopper =
                new GraphHopperManaged(config.getGraphHopperConfiguration(), bootstrap.getObjectMapper());
        GraphHopper configuredGraphHopper = graphHopper.getGraphHopper();
        configuredGraphHopper.load(configuredGraphHopper.getGraphHopperLocation());

        // Load OSM info needed for export from MapDB database file
        DB db = DBMaker.newFileDB(new File("transit_data/osm_info.db")).readOnly().make();
        Map<Long, Map<String, String>> osmIdToLaneTags = db.getHashMap("osmIdToLaneTags");
        Map<Integer, Long> ghIdToOsmId = db.getHashMap("ghIdToOsmId");
        Map<Long, List<String>> osmIdToAccessFlags = db.getHashMap("osmIdToAccessFlags");
        Map<Long, String> osmIdToStreetName = db.getHashMap("osmIdToStreetName");
        Map<Long, String> osmIdToHighway = db.getHashMap("osmIdToHighway");

        // Copied from writeStreetEdgesCsv
        StreetEdgeExporter exporter = new StreetEdgeExporter(configuredGraphHopper, osmIdToLaneTags, ghIdToOsmId, osmIdToAccessFlags, osmIdToStreetName, osmIdToHighway);
        GraphHopperStorage graphHopperStorage = configuredGraphHopper.getGraphHopperStorage();
        AllEdgesIterator edgeIterator = graphHopperStorage.getAllEdges();

        // Generate the rows for the first item in the edge iterator
        edgeIterator.next();
        List<StreetEdgeExportRecord> records = exporter.generateRecords(edgeIterator);
        // Expect that two items will be generated
        assertEquals(2, records.size());
        // They should be each other's reverse edges
        StreetEdgeExportRecord record0 = records.get(0);
        StreetEdgeExportRecord record1 = records.get(1);
        assertEquals(record0.startVertexId, record1.endVertexId);
        assertEquals(record0.endVertexId, record1.startVertexId);
        assertEquals(record0.startLat, record1.endLat);
        assertEquals(record0.startLon, record1.endLon);

    }
}
