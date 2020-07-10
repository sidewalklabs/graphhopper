package com.graphhopper.swl;

import java.util.Map;

public class Way {

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
