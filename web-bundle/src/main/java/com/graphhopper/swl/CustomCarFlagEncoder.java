package com.graphhopper.swl;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.util.PMap;

public class CustomCarFlagEncoder extends CarFlagEncoder {
    private String name;

    public CustomCarFlagEncoder(PMap properties, String name) {
        super(properties);
        this.name = name;
        if (!properties.getBool("block_private", true)) {
            this.restrictedValues.remove("private");
            this.intendedValues.add("private");
            this.intendedValues.add("destination");
        }

        if (!properties.getBool("block_delivery", true)) {
            this.restrictedValues.remove("delivery");
            this.intendedValues.add("delivery");
        }
    }

    protected final double applyMaxSpeed(ReaderWay way, double speed) {
        double maxSpeed = this.getMaxSpeed(way);
        return maxSpeed >= 0.0D ? Math.max(5.0D, Math.min((double)this.maxPossibleSpeed, maxSpeed)) : speed;
    }

    public double getMaxSpeed() {
        return Math.min(super.getMaxSpeed(), (double)this.maxPossibleSpeed);
    }

    public int getVersion() {
        return 3;
    }

    @Override
    public String toString() {
        return name;
    }
}
