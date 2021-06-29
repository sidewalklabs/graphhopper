package com.replica;

import com.graphhopper.GraphHopper;
import com.graphhopper.replica.StreetEdgeExportRecord;
import com.graphhopper.replica.StreetEdgeExporter;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.storage.GraphHopperStorage;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mapdb.DB;
import org.mapdb.DBMaker;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class StreetEdgeExporterTest extends ReplicaGraphHopperTest {

    @Test
    public void testExportEndToEnd() throws IOException {
        cli.run("export", TEST_GRAPHHOPPER_CONFIG_PATH);
        CSVFormat format = StreetEdgeExporter.CSV_FORMAT;
        File expectedOutputLocation = new File(GRAPH_FILES_DIR + "street_edges.csv");
        CSVParser parser = CSVParser.parse(expectedOutputLocation, StandardCharsets.UTF_8, format);
        List<CSVRecord> records = parser.getRecords();
        assertEquals(1869, records.size());
    }

    @Test
    public void testExportSingleRecord() throws Exception {
        // TODO copied from ExportCommand
        GraphHopper configuredGraphHopper = graphHopperManaged.getGraphHopper();

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
