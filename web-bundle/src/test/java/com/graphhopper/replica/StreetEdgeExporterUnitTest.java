package com.graphhopper.replica;

import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.util.Helper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;


public class StreetEdgeExporterUnitTest {
    private static final String TARGET_DIR = "./target/gtfs-app-gh/";

    @BeforeAll
    public static void setUp() {
        // Fresh target directory
        Helper.removeDir(new File(TARGET_DIR));
    }

    @AfterAll
    public static void cleanUp() {
        Helper.removeDir(new File(TARGET_DIR));
    }


    @Test
    public void testSomething() {
        GraphHopperConfig config = new GraphHopperConfig();
        config.putObject("graph.location", TARGET_DIR);
        GraphHopper configuredGraphHopper = new GraphHopper();
        configuredGraphHopper.init(config);

        configuredGraphHopper.importOrLoad();

    }
}
