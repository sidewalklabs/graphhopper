package com.replica;

import com.google.common.base.Preconditions;
import com.graphhopper.config.Profile;
import com.graphhopper.http.GraphHopperApplication;
import com.graphhopper.http.GraphHopperBundle;
import com.graphhopper.http.GraphHopperServerConfiguration;
import com.graphhopper.http.cli.ExportCommand;
import com.graphhopper.http.cli.GtfsLinkMapperCommand;
import com.graphhopper.http.cli.ImportCommand;
import com.graphhopper.util.Helper;
import io.dropwizard.Application;
import io.dropwizard.cli.Cli;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.util.JarLocation;
import org.junit.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.io.File;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StreetEdgeExporterIntegrationTest {

    private static final String TARGET_DIR = "./target/gtfs-app-gh/";
    private static final String TRANSIT_DATA_DIR = "transit_data/";
    public static final DropwizardAppExtension<GraphHopperServerConfiguration> app = new DropwizardAppExtension<>(GraphHopperApplication.class, createConfig());
    private static GraphHopperServerConfiguration createConfig() {
        GraphHopperServerConfiguration config = new GraphHopperServerConfiguration();
        config.getGraphHopperConfiguration().
                putObject("graph.flag_encoders", "foot").
                putObject("datareader.file", "test-data/beatty.osm").
                putObject("gtfs.file", "test-data/sample-feed.zip").
                putObject("graph.location", TARGET_DIR).
                setProfiles(Collections.singletonList(new Profile("foot").setVehicle("foot").setWeighting("fastest")));
        return config;
    }

    @BeforeAll
    public static void setUp() {
        // Fresh target directory
        Helper.removeDir(new File(TARGET_DIR));
        // Create new empty directory for GTFS/OSM resources
        File transitDataDir = new File(TRANSIT_DATA_DIR);
        if (transitDataDir.exists()) {
            throw new IllegalStateException(TRANSIT_DATA_DIR + " directory should not already exist.");
        }
        Preconditions.checkState(transitDataDir.mkdir(), "could not create directory " + TRANSIT_DATA_DIR);


//        bootstrap.addCommand(new GtfsLinkMapperCommand());


//        cli.run("gtfs_links", "test-data/beatty-sample-feed-config.yml");
    }

    @AfterAll
    public static void cleanUp() {
        Helper.removeDir(new File(TARGET_DIR));
        Helper.removeDir(new File(TRANSIT_DATA_DIR));
    }

    @Test
    public void testSomething() {
        // Setup necessary mock
        final JarLocation location = mock(JarLocation.class);
        when(location.getVersion()).thenReturn(Optional.of("1.0.0"));

        // Add commands you want to test
        final Bootstrap<GraphHopperServerConfiguration> bootstrap = new Bootstrap<>(new GraphHopperApplication());
        bootstrap.addBundle(new GraphHopperBundle());
        bootstrap.addCommand(new ImportCommand());
        bootstrap.addCommand(new ExportCommand());

        // Build what'll run the command and interpret arguments
        Cli cli = new Cli(location, bootstrap, System.out, System.err);
        cli.run("import", "test-data/beatty-sample-feed-config.yml");
//        cli.run("export", "sadfsadfasd");
        System.out.println("df");
    }


}
