package com.replica;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.http.GraphHopperApplication;
import com.graphhopper.http.GraphHopperBundle;
import com.graphhopper.http.GraphHopperManaged;
import com.graphhopper.http.GraphHopperServerConfiguration;
import com.graphhopper.http.cli.ExportCommand;
import com.graphhopper.http.cli.GtfsLinkMapperCommand;
import com.graphhopper.http.cli.ImportCommand;
import com.graphhopper.jackson.GraphHopperConfigModule;
import com.graphhopper.jackson.Jackson;
import com.graphhopper.util.Helper;
import io.dropwizard.cli.Cli;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.util.JarLocation;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.io.File;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ReplicaGraphHopperTest {
    protected static final String GRAPH_FILES_DIR = "transit_data/graphhopper/";
    protected static final String TRANSIT_DATA_DIR = "transit_data/";
    protected static final String TEST_GRAPHHOPPER_CONFIG_PATH = "../test_gh_config.yaml";
    protected static final String TEST_REGION_NAME = "mini_kc";
    protected static final String TEST_GTFS_FILE_NAME = "mini_kc_gtfs.tar";

    protected static Bootstrap<GraphHopperServerConfiguration> bootstrap;
    protected static Cli cli;
    protected static GraphHopperConfig graphHopperConfiguration = null;
    protected static GraphHopperManaged graphHopperManaged = null;

    @BeforeAll
    public static void setUp() throws Exception {
        // Fresh target directory
        Helper.removeDir(new File(GRAPH_FILES_DIR));
        Helper.removeDir(new File(TRANSIT_DATA_DIR));

        // Setup necessary mock
        final JarLocation location = mock(JarLocation.class);
        when(location.getVersion()).thenReturn(Optional.of("1.0.0"));

        // Add commands you want to test
        bootstrap = new Bootstrap<>(new GraphHopperApplication());
        bootstrap.addBundle(new GraphHopperBundle());
        bootstrap.addCommand(new ImportCommand());
        bootstrap.addCommand(new ExportCommand());
        bootstrap.addCommand(new GtfsLinkMapperCommand());

        // Run commands to build graph and GTFS link mappings for test region
        cli = new Cli(location, bootstrap, System.out, System.err);
        cli.run("import", TEST_GRAPHHOPPER_CONFIG_PATH);
        cli.run("gtfs_links", TEST_GRAPHHOPPER_CONFIG_PATH);

        loadGraphhopper();
    }

    @AfterAll
    public static void cleanUp() {
        Helper.removeDir(new File(GRAPH_FILES_DIR));
        Helper.removeDir(new File(TRANSIT_DATA_DIR));
        graphHopperManaged.getGraphHopper().close();
    }

    private static void loadGraphhopper() throws Exception {
        ObjectMapper yaml = Jackson.initObjectMapper(new ObjectMapper(new YAMLFactory()));
        yaml.registerModule(new GraphHopperConfigModule());
        JsonNode yamlNode = yaml.readTree(new File(TEST_GRAPHHOPPER_CONFIG_PATH));
        graphHopperConfiguration = yaml.convertValue(yamlNode.get("graphhopper"), GraphHopperConfig.class);
        ObjectMapper json = Jackson.newObjectMapper();
        graphHopperManaged = new GraphHopperManaged(graphHopperConfiguration, json);
        graphHopperManaged.start();
    }
}
