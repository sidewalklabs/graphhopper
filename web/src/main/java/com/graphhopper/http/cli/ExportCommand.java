package com.graphhopper.http.cli;

import com.graphhopper.GraphHopper;
import com.graphhopper.http.GraphHopperManaged;
import com.graphhopper.http.GraphHopperServerConfiguration;
import com.graphhopper.replica.StreetEdgeExporter;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Custom command used to export a GraphHopper street network in CSV format. Developed to mimic as much as possible
 * the logic from R5's built-in street network export command.
 *
 * To run, the command expects an OSM file for the region to be specified with GraphHopper's
 * -Ddw.graphhopper.datareader.file command line argument, and pre-built graph files (built from the same OSM using
 * GH's import command) to be present in the /transit_data/graphhopper subfolder.
 *
 * Example of calling this command:
 * java -Xmx10g -Ddw.graphhopper.datareader.file=./region_cutout.osm.pbf \
 * -jar web/target/graphhopper-web-1.0-SNAPSHOT.jar export config-usa.yml
 */

public class ExportCommand extends ConfiguredCommand<GraphHopperServerConfiguration> {
    private static final Logger logger = LoggerFactory.getLogger(ExportCommand.class);

    public ExportCommand() {
        super("export", "Generates street network CSV file from a GH graph");
    }

    @Override
    protected void run(Bootstrap<GraphHopperServerConfiguration> bootstrap, Namespace namespace,
                       GraphHopperServerConfiguration configuration) {
        // Read in pre-built GH graph files from /transit_data/graphhopper
        final GraphHopperManaged graphHopper =
                new GraphHopperManaged(configuration.getGraphHopperConfiguration(), bootstrap.getObjectMapper());
        GraphHopper configuredGraphHopper = graphHopper.getGraphHopper();
        if (!configuredGraphHopper.load(configuredGraphHopper.getGraphHopperLocation())) {
            throw new RuntimeException("Couldn't load existing GH graph at " +
                    configuredGraphHopper.getGraphHopperLocation());
        }

        // Load OSM info needed for export from MapDB database file
        DB db = DBMaker.newFileDB(new File("transit_data/osm_info.db")).readOnly().make();
        Map<Long, Map<String, String>> osmIdToLaneTags = db.getHashMap("osmIdToLaneTags");
        Map<Integer, Long> ghIdToOsmId = db.getHashMap("ghIdToOsmId");
        Map<Long, List<String>> osmIdToAccessFlags = db.getHashMap("osmIdToAccessFlags");
        Map<Long, String> osmIdToStreetName = db.getHashMap("osmIdToStreetName");
        Map<Long, String> osmIdToHighway = db.getHashMap("osmIdToHighway");
        logger.info("Done loading OSM info needed for CSV export from MapDB file.");

        // Use loaded graph data to write street network out to CSV
        StreetEdgeExporter.writeStreetEdgesCsv(configuredGraphHopper, osmIdToLaneTags, ghIdToOsmId,
                osmIdToAccessFlags, osmIdToStreetName, osmIdToHighway);
        db.close();
    }
}
