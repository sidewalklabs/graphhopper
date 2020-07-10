package com.graphhopper.swl;

import com.google.common.collect.Maps;
import com.graphhopper.reader.DataReader;
import com.graphhopper.reader.ReaderElement;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.reader.osm.OSMInput;
import com.graphhopper.reader.osm.OSMReader;
import com.graphhopper.storage.GraphHopperStorage;

import java.io.File;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

public class CustomGraphHopperOSM extends GraphHopperOSM {

    private String osmPath;
    private Map<Long, String> osmIdToLanesTag;
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
    protected DataReader createReader(GraphHopperStorage ghStorage) {
        OSMReader reader = new OSMReader(ghStorage) {

            {
                try (OSMInput input = this.openOsmInputFile(new File(osmPath))){
                    TraversalPermissionLabeler flagLabeler = new USTraversalPermissionLabeler();
                    ReaderElement next;
                    while((next = input.getNext()) != null) {
                        if (next.isType(ReaderElement.WAY)) {
                            final ReaderWay ghReaderWay = (ReaderWay) next;

                            if(ghReaderWay.hasTag("lanes")) {
                                osmIdToLanesTag.put(ghReaderWay.getId(), ghReaderWay.getTag("lanes"));
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

    public String getLanesTag(long osmId) {
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
