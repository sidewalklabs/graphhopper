package com.graphhopper.swl;

import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.util.PMap;

public class CustomCarFlagEncoder extends CarFlagEncoder {
    private String name;

    public CustomCarFlagEncoder(PMap properties, String name) {
        super(properties);
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
