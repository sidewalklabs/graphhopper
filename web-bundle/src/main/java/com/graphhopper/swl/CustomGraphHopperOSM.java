package com.graphhopper.swl;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.graphhopper.reader.DataReader;
import com.graphhopper.reader.ReaderElement;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.reader.osm.OSMInput;
import com.graphhopper.reader.osm.OSMReader;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.GraphHopperStorage;

import java.io.File;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CustomGraphHopperOSM extends GraphHopperOSM {
    private static final Set<String> LANE_TAGS = Sets.newHashSet("lanes", "lanes:forward", "lanes:backward");

    private String osmPath;
    private Map<Long, Map<String, String>> osmIdToLanesTag;
    private Map<Integer, Long> ghToOsmIds;
    private Map<Long, String[]> osmIdToOsmTags;

    public CustomGraphHopperOSM(String osmPath) {
        super();
        this.osmPath = osmPath;
        this.osmIdToLanesTag = Maps.newHashMap();
        this.ghToOsmIds = Maps.newHashMap();
        this.osmIdToOsmTags = Maps.newHashMap();
    }

    @Override
    protected void registerCustomEncodedValues(EncodingManager.Builder emBuilder) {
        StableIdEncodedValues.createAndAddEncodedValues(emBuilder);
    }

    @Override
    protected DataReader createReader(GraphHopperStorage ghStorage) {
        OSMReader reader = new OSMReader(ghStorage) {

            {
                try (OSMInput input = this.openOsmInputFile(new File(osmPath))){
                    TraversalPermissionLabeler flagLabeler = new USTraversalPermissionLabeler();
                    ReaderElement next;
                    while((next = input.getNext()) != null) {
                        if (next.isType(ReaderElement.WAY)) {
                            final ReaderWay ghReaderWay = (ReaderWay) next;
                            long osmId = ghReaderWay.getId();

                            for (String laneTag : LANE_TAGS) {
                                if (ghReaderWay.hasTag(laneTag)) {
                                    if (osmIdToLanesTag.containsKey(osmId)) {
                                        Map<String, String> currentLaneTags = osmIdToLanesTag.get(osmId);
                                        currentLaneTags.put(laneTag, ghReaderWay.getTag(laneTag));
                                        osmIdToLanesTag.put(osmId, currentLaneTags);
                                    } else {
                                        Map<String, String> newLaneTags = Maps.newHashMap();
                                        newLaneTags.put(laneTag, ghReaderWay.getTag(laneTag));
                                        osmIdToLanesTag.put(osmId, newLaneTags);
                                    }
                                }
                            }

                            Map<String, String> wayTagsToConsider = Maps.newHashMap();
                            for (String consideredTag : flagLabeler.getAllConsideredTags()) {
                                if (ghReaderWay.hasTag(consideredTag)) {
                                    wayTagsToConsider.put(consideredTag, ghReaderWay.getTag(consideredTag));
                                }
                            }
                            Way way = new Way(wayTagsToConsider);
                            List<EnumSet<TraversalPermissionLabeler.EdgeFlag>> flags = flagLabeler.getPermissions(way);
                            String[] flagStrings = {flags.get(0).toString(), flags.get(1).toString()};
                            osmIdToOsmTags.put(ghReaderWay.getId(), flagStrings);
                        }
                    }

                } catch (Exception e) {
                    throw new RuntimeException("Can't open OSM file provided at " + osmPath + "!");
                }
            }

            @Override
            protected void storeOsmWayID(int edgeId, long osmWayId) {
                super.storeOsmWayID(edgeId, osmWayId);
                ghToOsmIds.put(edgeId, osmWayId);
            }
        };
        return initDataReader(reader);
    }

    public Map<String, String> getLanesTag(long osmId) {
        return osmIdToLanesTag.containsKey(osmId) ? osmIdToLanesTag.get(osmId) : null;
    }

    public long getOsmIdForGhEdge(int ghEdgeId) {
        return ghToOsmIds.get(ghEdgeId);
    }

    // Sets of flags are returned for each edge direction, stored in a String[] ordered [forward, backward]
    public String getFlagsForGhEdge(int ghEdgeId, boolean reverse) {
        int flagIndex = reverse ? 1 : 0;
        return osmIdToOsmTags.get(getOsmIdForGhEdge(ghEdgeId))[flagIndex];
    }
}
