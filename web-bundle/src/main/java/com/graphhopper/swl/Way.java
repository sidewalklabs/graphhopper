package com.graphhopper.swl;

import java.util.Map;

/**
 * Custom class created to store OSM tags for a specific Way, needed for computing columns in the street network
 * export CSV.
 */

public class Way {

    // Map of OSM tag name -> tag value for this way, set at object construction time
    private Map<String, String> tags;

    public Way(Map<String, String> tags) {
        this.tags = tags;
    }

    public boolean hasTag(String tag) {
        return tags.containsKey(tag);
    }

    public boolean hasTag(String key, String value) {
        return value.equals(this.getTag(key));
    }

    public String getTag(String tag) {
        return tags.get(tag);
    }
}
