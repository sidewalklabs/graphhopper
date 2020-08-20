package com.graphhopper.swl;

import com.google.common.collect.Maps;
import com.graphhopper.reader.ReaderWay;

import java.util.Map;
import java.util.Set;

/**
 * Custom class created to store OSM tags for a specific Way, needed for computing columns in the street network
 * export CSV.
 */

public class Way {

    // Map of OSM tag name -> tag value for this way, set at object construction time
    private Map<String, String> tags;

    public Way(ReaderWay ghReaderWay) {
        // Parse all tags that will be considered for determining accessibility flags and edge type for way
        Set<String> allTagsToConsider = USTraversalPermissionLabeler.getAllConsideredTags();
        allTagsToConsider.addAll(TypeOfEdgeLabeler.getAllConsideredTags());

        this.tags = Maps.newHashMap();
        for (String consideredTag : allTagsToConsider) {
            if (ghReaderWay.hasTag(consideredTag)) {
                this.tags.put(consideredTag, ghReaderWay.getTag(consideredTag));
            }
        }
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
