package com.graphhopper;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.graphhopper.export.TraversalPermissionLabeler;
import com.graphhopper.export.USTraversalPermissionLabeler;
import com.graphhopper.export.Way;
import com.graphhopper.json.geo.JsonFeatureCollection;
import com.graphhopper.reader.DataReader;
import com.graphhopper.reader.ReaderElement;
import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.reader.osm.OSMInput;
import com.graphhopper.reader.osm.OSMInputFile;
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
    // Map of OSM way ID to access flags for each edge direction (each created from set
    // {ALLOWS_CAR, ALLOWS_BIKE, ALLOWS_PEDESTRIAN}), stored in list in order [forward, backward]
    private Map<Long, List<String>> osmIdToAccessFlags;
    // Map of OSM ID to street name. Name is parsed directly from Way, unless name field isn't present,
    // in which case the name is taken from the Relation containing the Way, if one exists
    private Map<Long, String> osmIdToStreetName;
    // Map of OSM ID to highway tag
    private Map<Long, String> osmIdToHighwayTag;


    public CustomGraphHopperOSM(JsonFeatureCollection landmarkSplittingFeatureCollection, GraphHopperConfig ghConfig) {
        super(landmarkSplittingFeatureCollection);
        this.osmPath = ghConfig.getString("datareader.file", "");
        this.osmIdToLaneTags = Maps.newHashMap();
        this.ghIdToOsmId = Maps.newHashMap();
        this.osmIdToAccessFlags = Maps.newHashMap();
        this.osmIdToStreetName = Maps.newHashMap();
        this.osmIdToHighwayTag = Maps.newHashMap();
    }

    @Override
    protected void registerCustomEncodedValues(EncodingManager.Builder emBuilder) {
        super.registerCustomEncodedValues(emBuilder);
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
            // Hacky override used to populate GH ID -> OSM ID map; called during standard GH import process
            @Override
            protected void storeOsmWayID(int edgeId, long osmWayId) {
                super.storeOsmWayID(edgeId, osmWayId);
                ghIdToOsmId.put(edgeId, osmWayId);
            }
        };
        return initDataReader(reader);
    }

    public void collectOsmInfo() {
        LOG.info("Creating custom OSM reader; reading file and parsing lane tag and street name info.");
        List<ReaderRelation> roadRelations = Lists.newArrayList();
        int readCount = 0;
        try (OSMInput input = new OSMInputFile(new File(osmPath)).setWorkerThreads(2).open()) {
            TraversalPermissionLabeler flagLabeler = new USTraversalPermissionLabeler();
            ReaderElement next;
            while((next = input.getNext()) != null) {
                if (next.isType(ReaderElement.WAY)) {
                    if (++readCount % 10000 == 0) {
                        LOG.info("Parsing tag info from OSM ways. " + readCount + " read so far.");
                    }
                    final ReaderWay ghReaderWay = (ReaderWay) next;
                    long osmId = ghReaderWay.getId();

                    // Parse street name from Way, if it exists
                    String wayName = getNameFromOsmElement(ghReaderWay);
                    if (wayName != null) {
                        osmIdToStreetName.put(osmId, wayName);
                    }

                    // Parse highway tag from Way, if it's present
                    String highway = getHighwayFromOsmWay(ghReaderWay);
                    if (highway != null) {
                        osmIdToHighwayTag.put(osmId, highway);
                    }

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
                    List<String> flagStrings = Lists.newArrayList(flags.get(0).toString(), flags.get(1).toString());
                    osmIdToAccessFlags.put(ghReaderWay.getId(), flagStrings);
                } else if (next.isType(ReaderElement.RELATION)) {
                    if (next.hasTag("route", "road")) {
                        roadRelations.add((ReaderRelation) next);
                    }
                }
            }
            LOG.info("Finished parsing lane tag info from OSM ways. " + readCount + " total ways were parsed.");

            readCount = 0;
            LOG.info("Scanning road relations to populate street names for Ways that didn't have them set.");
            for (ReaderRelation relation : roadRelations) {
                if (relation.hasTag("route", "road")) {
                    if (++readCount % 1000 == 0) {
                        LOG.info("Parsing tag info from OSM relations. " + readCount + " read so far.");
                    }
                    for (ReaderRelation.Member member : relation.getMembers()) {
                        if (member.getType() == ReaderRelation.Member.WAY) {
                            // If we haven't recorded a street name for a Way in this Relation,
                            // use the Relation's name instead, if it exists
                            if (!osmIdToStreetName.containsKey(member.getRef())) {
                                String streetName = getNameFromOsmElement(relation);
                                if (streetName != null) {
                                    osmIdToStreetName.put(member.getRef(), streetName);
                                }
                            }
                        }
                    }
                }
            }
            LOG.info("Finished scanning road relations for additional street names. " + readCount + " total relations were considered.");
        } catch (Exception e) {
            throw new RuntimeException("Can't open OSM file provided at " + osmPath + "!");
        }
    }

    private static String getHighwayFromOsmWay(ReaderWay way) {
        if (way.hasTag("highway")) {
            return way.getTag("highway");
        } else {
            return null;
        }
    }

    private static String getNameFromOsmElement(ReaderElement wayOrRelation) {
        if (wayOrRelation.hasTag("name")) {
            return wayOrRelation.getTag("name");
        } else if (wayOrRelation.hasTag("ref")) {
            return wayOrRelation.getTag("ref");
        } else {
            return null;
        }
    }

    public Map<Long, Map<String, String>> getOsmIdToLaneTags() {
        return osmIdToLaneTags;
    }

    public Map<Integer, Long> getGhIdToOsmId() {
        return ghIdToOsmId;
    }

    public Map<Long, List<String>> getOsmIdToAccessFlags() {
        return osmIdToAccessFlags;
    }

    public Map<Long, String> getOsmIdToStreetName() {
        return osmIdToStreetName;
    }

    public Map<Long, String> getOsmIdToHighwayTag() {
        return osmIdToHighwayTag;
    }
}
