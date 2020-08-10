package com.graphhopper.http.cli;

import com.graphhopper.GraphHopper;
import com.graphhopper.http.GraphHopperManaged;
import com.graphhopper.http.GraphHopperServerConfiguration;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;

public class GtfsLinkMapperCommand extends ConfiguredCommand<GraphHopperServerConfiguration> {

    public GtfsLinkMapperCommand() {
        super("gtfs_links", "creates db linking stable edge IDs from street network to each GTFS stop-stop segment");
    }

    @Override
    protected void run(Bootstrap<GraphHopperServerConfiguration> bootstrap, Namespace namespace, GraphHopperServerConfiguration configuration) {
        final GraphHopperManaged graphHopper = new GraphHopperManaged(configuration.getGraphHopperConfiguration(), bootstrap.getObjectMapper());
        GraphHopper gh = graphHopper.getGraphHopper();
        gh.importOrLoad();
        graphHopper.setGtfsLinkMappings();
        gh.close();
    }
}
