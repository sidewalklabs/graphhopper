package com.graphhopper.http.cli;

import com.graphhopper.CustomGraphHopperOSM;
import com.graphhopper.http.GraphHopperManaged;
import com.graphhopper.http.GraphHopperServerConfiguration;
import com.graphhopper.replica.StableEdgeIdManager;
import com.graphhopper.replica.StreetEdgeExporter;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExportNationwideCommand extends ConfiguredCommand<GraphHopperServerConfiguration> {
    private static final Logger logger = LoggerFactory.getLogger(ExportNationwideCommand.class);

    public ExportNationwideCommand() {
        super("export-nationwide", "Imports nationwide OSM + generates street network CSV file");
    }

    @Override
    protected void run(Bootstrap<GraphHopperServerConfiguration> bootstrap, Namespace namespace,
                       GraphHopperServerConfiguration configuration) {
        final GraphHopperManaged graphHopper = new GraphHopperManaged(configuration.getGraphHopperConfiguration(), bootstrap.getObjectMapper());

        // Build OSM-only GH graph, collect OSM tag info, and set stable edge IDs (as done in normal import)
        CustomGraphHopperOSM gh = (CustomGraphHopperOSM) graphHopper.getGraphHopper();
        gh.importOrLoad();
        gh.collectOsmInfo();
        StableEdgeIdManager stableEdgeIdManager = new StableEdgeIdManager(gh);
        stableEdgeIdManager.setStableEdgeIds();
        logger.info("Done building graph from OSM, parsing tags, and setting stable edge IDs");

        // Write processed street network out to CSV
        StreetEdgeExporter.writeStreetEdgesCsv(gh, gh.getOsmIdToLaneTags(), gh.getGhIdToOsmId(),
                gh.getOsmIdToAccessFlags(), gh.getOsmIdToStreetName(), gh.getOsmIdToHighwayTag());
        gh.close();
    }
}
