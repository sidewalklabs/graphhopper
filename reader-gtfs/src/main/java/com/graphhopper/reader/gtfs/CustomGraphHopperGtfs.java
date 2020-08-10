package com.graphhopper.reader.gtfs;

import com.graphhopper.GraphHopperConfig;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.stableid.StableIdEncodedValues;

/**
 * Custom implementation of GraphHopperGtfs that overrides registerCustomEncodedValues() to ensure the encoded
 * values for storing stable edge IDs are registered properly. Unfortunately, we can't simply declare a Java
 * anonymous class and override this function inline in the constructor of GraphHopperManaged, as we do with 
 * GraphHopperOSM.registerCustomEncodedValues(); this causes problems for GraphHopperBundle.GtfsStorageFactory,
 * as the anonymous function is no longer viewed by Java as being of class GraphHopperGtfs.
 */

public class CustomGraphHopperGtfs extends GraphHopperGtfs {

    public CustomGraphHopperGtfs(GraphHopperConfig ghConfig) {
        super(ghConfig);
    }

    @Override
    protected void registerCustomEncodedValues(EncodingManager.Builder emBuilder) {
        PtEncodedValues.createAndAddEncodedValues(emBuilder);
        StableIdEncodedValues.createAndAddEncodedValues(emBuilder);
    }
}
