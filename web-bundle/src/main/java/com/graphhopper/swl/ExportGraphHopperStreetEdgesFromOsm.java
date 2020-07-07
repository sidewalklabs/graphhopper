package com.graphhopper.swl;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.osm.OSMReader;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.DefaultFlagEncoderFactory;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.DAType;
import com.graphhopper.storage.GHDirectory;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint3D;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ExportGraphHopperStreetEdgesFromOsm {

    private static final String FLAG_ENCODERS = "car,bike,foot";

    public static void main(String[] args) throws IOException {
        String ghLocation = args[0];
        String osmLocation = args[1];
        String csvOutputLocation = args[2];

        EncodingManager encodingManager = EncodingManager.create(new DefaultFlagEncoderFactory(), FLAG_ENCODERS);

        GraphHopperStorage graphHopperStorage = new GraphHopperStorage(new GHDirectory(ghLocation, DAType.RAM_STORE), encodingManager, false);
        OSMReader reader = new OSMReader(graphHopperStorage);
        reader.setFile(new File(osmLocation));
        reader.readGraph();

        System.out.println(encodingManager.fetchEdgeEncoders());

        AllEdgesIterator edgesIterator = graphHopperStorage.getAllEdges();
        List<String> outputLines = new ArrayList<>();
        outputLines.add("gh_edge_id,geometry"); //todo: update

        while(edgesIterator.next()) {
            int ghEdgeId = edgesIterator.getEdge();
            String stableEdgeId = ""; //todo: add calculated ID once stable ID function is finalized

            int startVertex = edgesIterator.getBaseNode();
            int endVertex = edgesIterator.getAdjNode();
            PointList wayGeometry = edgesIterator.fetchWayGeometry(FetchMode.ALL);
            String geometryString = "\"" + wayGeometry.toLineString(false).toString() + "\"";
            GHPoint3D baseNode = wayGeometry.get(edgesIterator.getBaseNode());
            GHPoint3D adjNode = wayGeometry.get(edgesIterator.getAdjNode());
            double startLat = baseNode.getLat();
            double startLong = baseNode.getLon();
            double endLat = adjNode.getLat();
            double endLong = adjNode.getLon();
            double distance = Math.round(wayGeometry.calcDistance(new DistanceCalcEarth()));

            // todo: all of below need to be tested, not sure if these are correct
            ReaderWay way = new ReaderWay(reader.getOsmIdOfInternalEdge(ghEdgeId));
            long osmId = way.getId();
            String speed = way.getTag("speed");
            String flags = edgesIterator.getFlags().toString();
            String lanes = way.getTag("lanes");
            String highway = way.getTag("highway");
            String directionRole = way.getTag("directionRole");


            String line = Stream.of("hi").collect(Collectors.joining(",")); //todo: update w/ actual data
            outputLines.add(line);
        }

        File outputFile = new File(csvOutputLocation);
        try (PrintWriter pw = new PrintWriter(outputFile)) {
            outputLines.forEach(pw::println);
        } catch (FileNotFoundException f) {
            System.out.println("output file not found!");
        }
        assert(outputFile.exists());
    }
}
