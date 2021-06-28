package com.graphhopper.replica;

public class StreetEdgeExportRecord {
    public String edgeId;
    public int startVertexId;
    public int endVertexId;
    public double startLat;
    public double startLon;
    public double endLat;
    public double endLon;
    public String geometryString;
    public String streetName;
    public long distanceMillimeters;
    public long osmId;
    public int speedCms;
    public String flags;
    public int lanes;
    public String highwayTag;

    public StreetEdgeExportRecord(String edgeId, int startVertexId, int endVertexId, double startLat, double startLon, double endLat, double endLon, String geometryString, String streetName, long distanceMillimeters, long osmId, int speedCms, String flags, int lanes, String highwayTag) {
        this.edgeId = edgeId;
        this.startVertexId = startVertexId;
        this.endVertexId = endVertexId;
        this.startLat = startLat;
        this.startLon = startLon;
        this.endLat = endLat;
        this.endLon = endLon;
        this.geometryString = geometryString;
        this.streetName = streetName;
        this.distanceMillimeters = distanceMillimeters;
        this.osmId = osmId;
        this.speedCms = speedCms;
        this.flags = flags;
        this.lanes = lanes;
        this.highwayTag = highwayTag;
    }
}

