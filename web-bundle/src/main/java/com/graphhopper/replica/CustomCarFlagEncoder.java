package com.graphhopper.replica;

import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.util.PMap;

public class CustomCarFlagEncoder extends CarFlagEncoder {
    private String name;

    public CustomCarFlagEncoder(PMap properties, String name) {
        super(properties);
        this.name = name;
    }

    public int getVersion() {
        return 3;
    }

    @Override
    public String toString() {
        return name;
    }
}
