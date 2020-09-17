package com.graphhopper.export;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.graphhopper.reader.DataReader;
import com.graphhopper.reader.ReaderElement;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.reader.osm.OSMInput;
import com.graphhopper.reader.osm.OSMReader;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.stableid.StableIdEncodedValues;
import com.graphhopper.storage.GraphHopperStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Custom implementation of internal class GraphHopper uses to parse OSM files into GH's internal graph data structures.
 * In particular, the purpose of this class is to parse and store specific OSM tag information needed to replicate the
 * logic used to create the `lanes` and `flags` columns of R5's street network CSV export.
 *
 * If needed, this class can easily be extended in the future to parse other OSM tag information for use in exporting
 * data about a particular region's GH street network.
 */

public class CustomGraphHopperOSM extends GraphHopperOSM {
    private static final Logger LOG = LoggerFactory.getLogger(CustomGraphHopperOSM.class);

    // Tags considered by R5 when calculating the value of the `lanes` column
    private static final Set<String> LANE_TAGS = Sets.newHashSet("lanes", "lanes:forward", "lanes:backward");
    private String osmPath;

    // Map of OSM way ID -> (Map of OSM lane tag name -> tag value)
    private Map<Long, Map<String, String>> osmIdToLaneTags;
    // Map of GH edge ID to OSM way ID
    private Map<Integer, Long> ghIdToOsmId;
    // Map of OSM way ID to String[] of access flags (from set {ALLOWS_CAR, ALLOWS_BIKE, ALLOWS_PEDESTRIAN}) for each
    // edge direction, in order [forward, backward]
    private Map<Long, String[]> osmIdToAccessFlags;

    public CustomGraphHopperOSM(String osmPath) {
        super();
        this.osmPath = osmPath;
        this.osmIdToLaneTags = Maps.newHashMap();
        this.ghIdToOsmId = Maps.newHashMap();
        this.osmIdToAccessFlags = Maps.newHashMap();
    }

    @Override
    protected void registerCustomEncodedValues(EncodingManager.Builder emBuilder) {
        StableIdEncodedValues.createAndAddEncodedValues(emBuilder);
    }

    /**
     * Override creation of OSM reader to read the file once at initialization time, for the sole purpose of storing
     * OSM information that will be used later in the export script.
     *
     * Note that this approach requires reading the OSM file twice: once during the static initialization code, and
     * once during the call to importOrLoad() in ExportCommand.java, which is where the modified storeOsmWayID method
     * overridden below is called to populate the ghIdToOsmId map.
     *
     * todo: figure out if it's possible to eliminate the need for two OSM read operations
     */
    @Override
    protected DataReader createReader(GraphHopperStorage ghStorage) {
        OSMReader reader = new OSMReader(ghStorage) {
            // Static code that runs during object creation; parses OSM tags needed for lane counts and access flags
            {
                LOG.info("Creating custom OSM reader; reading file and parsing lane tag info.");
                int readCount = 0;
                try (OSMInput input = this.openOsmInputFile(new File(osmPath))){
                    TraversalPermissionLabeler flagLabeler = new USTraversalPermissionLabeler();
                    ReaderElement next;
                    while((next = input.getNext()) != null) {
                        if (next.isType(ReaderElement.WAY)) {
                            if (++readCount % 10000 == 0) {
                                LOG.info("Parsing tag info from OSM ways. " + readCount + " read so far.");
                            }
                            final ReaderWay ghReaderWay = (ReaderWay) next;
                            long osmId = ghReaderWay.getId();

                            // Parse all tags needed for determining lane counts on edge
                            for (String laneTag : LANE_TAGS) {
                                if (ghReaderWay.hasTag(laneTag)) {
                                    if (osmIdToLaneTags.containsKey(osmId)) {
                                        Map<String, String> currentLaneTags = osmIdToLaneTags.get(osmId);
                                        currentLaneTags.put(laneTag, ghReaderWay.getTag(laneTag));
                                        osmIdToLaneTags.put(osmId, currentLaneTags);
                                    } else {
                                        Map<String, String> newLaneTags = Maps.newHashMap();
                                        newLaneTags.put(laneTag, ghReaderWay.getTag(laneTag));
                                        osmIdToLaneTags.put(osmId, newLaneTags);
                                    }
                                }
                            }

                            // Parse all tags that will be considered for determining accessibility flags for edge
                            Map<String, String> wayTagsToConsider = Maps.newHashMap();
                            for (String consideredTag : flagLabeler.getAllConsideredTags()) {
                                if (ghReaderWay.hasTag(consideredTag)) {
                                    wayTagsToConsider.put(consideredTag, ghReaderWay.getTag(consideredTag));
                                }
                            }

                            // Compute accessibility flags for edge in both directions
                            Way way = new Way(wayTagsToConsider);
                            List<EnumSet<TraversalPermissionLabeler.EdgeFlag>> flags = flagLabeler.getPermissions(way);
                            String[] flagStrings = {flags.get(0).toString(), flags.get(1).toString()};
                            osmIdToAccessFlags.put(ghReaderWay.getId(), flagStrings);
                        }
                    }
                    LOG.info("Finished parsing lane tag info from OSM ways. " + readCount + " total ways were parsed.");
                } catch (Exception e) {
                    throw new RuntimeException("Can't open OSM file provided at " + osmPath + "!");
                }
            }

            // Hacky override used to populate GH ID -> OSM ID map; called during standard GH import process
            @Override
            protected void storeOsmWayID(int edgeId, long osmWayId) {
                super.storeOsmWayID(edgeId, osmWayId);
                ghIdToOsmId.put(edgeId, osmWayId);
            }
        };
        return initDataReader(reader);
    }

    public Map<String, String> getLanesTag(long osmId) {
        return osmIdToLaneTags.containsKey(osmId) ? osmIdToLaneTags.get(osmId) : null;
    }

    public long getOsmIdForGhEdge(int ghEdgeId) {
        return ghIdToOsmId.get(ghEdgeId);
    }

    // Sets of flags are returned for each edge direction, stored in a String[] ordered [forward, backward]
    public String getFlagsForGhEdge(int ghEdgeId, boolean reverse) {
        int flagIndex = reverse ? 1 : 0;
        return osmIdToAccessFlags.get(getOsmIdForGhEdge(ghEdgeId))[flagIndex];
    }
}
