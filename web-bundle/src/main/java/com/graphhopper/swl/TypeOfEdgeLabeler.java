package com.graphhopper.swl;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * This sets the flags on edges indicating what category of edge they are, e.g. stairs, bike path, sidewalk.
 * Taken and modified from R5's TypeOfEdgeLabeler class.
 */

public class TypeOfEdgeLabeler {
    
    public enum EdgeFlag {
        STAIRS, BIKE_PATH, SIDEWALK, CROSSING
    }

    static final Set<String> allConsideredTags = Sets.newHashSet( "foot",
            "sidewalk", "bicycle", "cycleway", "cycleway:left", "cycleway:right", "segregated", "highway",
            "oneway", "footway", "name", "ref");

    public static Set<String> getAllConsideredTags() {
        return allConsideredTags;
    }

    private static boolean isCycleway (Way way, boolean back) {
        boolean bidirectionalCycleway = way.hasTag("highway", "cycleway") ||
                (way.hasTag("highway", "path") && way.hasTag("bicycle", "designated") && way.hasTag("foot", "designated")) ||
                way.hasTag("cycleway", "lane") ||
                way.hasTag("cycleway", "track");
        if (bidirectionalCycleway) {
            if (way.hasTag("oneway")) {
                if (TraversalPermissionLabeler.Label.fromTag(way.getTag("oneway")) == TraversalPermissionLabeler.Label.YES) {
                    if (!back) {
                        return true;
                    } else {
                        return false;
                    }
                }
            } else {
                return true;
            }
        }

        boolean has_cycleway_opposite = way.hasTag("cycleway", "opposite_lane") || way.hasTag("cycleway", "opposite_track");

        if (back) {
            String cycleway_left = way.getTag("cycleway:left");
            if (cycleway_left != null && TraversalPermissionLabeler.Label.fromTag(cycleway_left) == TraversalPermissionLabeler.Label.YES) {
                return true;
            }
            //if Oneway=true and has cycleway=opposite_lane/track return true on backward edge
            if (has_cycleway_opposite && way.hasTag("oneway")) {
                if (TraversalPermissionLabeler.Label.fromTag(way.getTag("oneway")) == TraversalPermissionLabeler.Label.YES) {
                    return true;
                }
            }
        } else {
            String cycleway_right = way.getTag("cycleway:right");
            if (cycleway_right != null && TraversalPermissionLabeler.Label.fromTag(cycleway_right) == TraversalPermissionLabeler.Label.YES) {
                return true;
            }
            //if Oneway=reverse and has cycleway=opposite_lane/track return true on forward edge
            if (has_cycleway_opposite && way.hasTag("oneway")) {
                if (way.getTag("oneway").equals("-1") || way.getTag("oneway").equals("reverse")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isSidewalk(Way way, boolean back) {
        //Road has a sidewalk
        if (way.hasTag("sidewalk")) {
            String sidewalk = way.getTag("sidewalk").toLowerCase();

            //sidewalks on both side
            if (sidewalk.equals("both")) {
                return true;
            } else if (sidewalk.equals("none") || sidewalk.equals("no") || sidewalk.equals("false")) {
                return false;
            }
            if (!back) {
                //sidewalk on the right for forward edge
                if (sidewalk.equals("right")) {
                    return true;
                }
            } else {
                //sidewalk on the left for backward edge
                if (sidewalk.equals("left")) {
                    return true;
                }
            }
            //sidewalk as separate way
        } else if (way.hasTag("highway", "footway") && way.hasTag("footway", "sidewalk")) {
            return true;
            //is implied to be sidewalk
        } else if ((way.hasTag("highway", "cycleway") && way.hasTag("foot", "designated")) ||
                (way.hasTag("highway", "path") && way.hasTag("bicycle", "designated") && way.hasTag("foot", "designated"))){
            return true;
            //implicit sidewalks with cycleways next to street
        } else if (way.hasTag("cycleway", "track") && way.hasTag("segregated", "yes")) {
            return true;
        }
        return false;
    }

    // todo: fix comment
    /**
     * Determines names for each edge direction, based on stairs, bike path,
     * sidewalk and crossing flags for a way
     */
    public static List<String> getEdgeNames(Way way) {
        EnumSet<EdgeFlag> forwardFlags = EnumSet.noneOf(EdgeFlag.class);
        EnumSet<EdgeFlag> backFlags = EnumSet.noneOf(EdgeFlag.class);

        if (way.hasTag("highway", "steps")) {
            forwardFlags.add(EdgeFlag.STAIRS);
            backFlags.add(EdgeFlag.STAIRS);
        }
        if (forwardFlags.contains(TraversalPermissionLabeler.EdgeFlag.ALLOWS_BIKE) && isCycleway(way , false)) {
            forwardFlags.add(EdgeFlag.BIKE_PATH);
        }
        if (backFlags.contains(TraversalPermissionLabeler.EdgeFlag.ALLOWS_BIKE) && isCycleway(way, true)) {
            backFlags.add(EdgeFlag.BIKE_PATH);
        }

        if (isSidewalk(way, false)) {
            forwardFlags.add(EdgeFlag.SIDEWALK);
        }
        if (isSidewalk(way, true)) {
            backFlags.add(EdgeFlag.SIDEWALK);
        }

        if (way.hasTag("footway", "crossing") || way.hasTag("cycleway", "crossing")) {
            forwardFlags.add(EdgeFlag.CROSSING);
            backFlags.add(EdgeFlag.CROSSING);
        }

        return Lists.newArrayList(getNameFromTags(way, forwardFlags), getNameFromTags(way, backFlags));
    }

    // todo: fix comment
    // only called if name found by GH was non-existent
    public static String getNameFromTags(Way way, Set<EdgeFlag> edgeTypeFlags) {
        //String name = getNameFromTags(way);
        //if (name == null) {
            if (edgeTypeFlags.contains(EdgeFlag.STAIRS)) {
                return "stairs";
            } else if (edgeTypeFlags.contains(EdgeFlag.CROSSING)) {
                return "street crossing";
            } else if (edgeTypeFlags.contains(EdgeFlag.BIKE_PATH)) {
                return "bike path";
            } else if (edgeTypeFlags.contains(EdgeFlag.SIDEWALK)) {
                return "sidewalk";
            } else return null;
        //}
        //return name;
    }

    // todo: try fixing/using this method, if I still have blank names
    /*
    private String getNameFromTags(Way way) {
        if (way.hasTag("name")) return way.getTag("name");
        if (way.hasTag("ref")) return way.getTag("ref");
        Fun.Tuple2<Long, Long> fromElement = new Fun.Tuple2<>(OSMid, Long.MIN_VALUE);
        Fun.Tuple2<Long, Long> toElement = new Fun.Tuple2<>(OSMid, Long.MAX_VALUE);
        NavigableSet<Fun.Tuple2<Long, Long>> tuple2s = osm.relationsByWay.subSet(fromElement, true, toElement, true);
        Stream<Relation> relationsThisWayIsMemberOf = tuple2s.stream().map(t -> osm.relations.get(t.b));
        String namesOfRoadsFromRelationsThisWayIsMemberOf = relationsThisWayIsMemberOf
                .filter(r -> r.hasTag("route", "road"))
                .flatMap(r -> {
                    if (r.hasTag("name")) return Stream.of(r.getTag("name"));
                    else if (r.hasTag("ref")) return Stream.of(r.getTag("ref"));
                    else return Stream.empty();
                }).collect(Collectors.joining(", "));
        if (!namesOfRoadsFromRelationsThisWayIsMemberOf.isEmpty()) {
            name = namesOfRoadsFromRelationsThisWayIsMemberOf;
        }

        return name;
    }
    */
}
