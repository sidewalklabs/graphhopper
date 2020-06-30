package com.graphhopper.swl;

import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.reader.osm.OSMReader;
import com.graphhopper.routing.ee.vehicles.CustomCarFlagEncoder;
import com.graphhopper.routing.ee.vehicles.TruckFlagEncoder;
import com.graphhopper.routing.ev.EncodedValueFactory;
import com.graphhopper.routing.util.*;
import com.graphhopper.storage.DAType;
import com.graphhopper.storage.GHDirectory;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.PMap;
import com.graphhopper.util.PointList;

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
        outputLines.add("gh_edge_id,geometry");

        while(edgesIterator.next()) {
            String ghEdgeId = "" + edgesIterator.getEdge();
            PointList wayGeometry = edgesIterator.fetchWayGeometry(FetchMode.ALL);
            String lineString = "\"" + wayGeometry.toLineString(false).toString() + "\"";
            String line = Stream.of(ghEdgeId, lineString).collect(Collectors.joining(","));
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
