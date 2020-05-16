/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing.template;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.ResponsePath;
import com.graphhopper.routing.*;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.RoadClass;
import com.graphhopper.routing.ev.RoadEnvironment;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.NameSimilarityEdgeFilter;
import com.graphhopper.routing.util.SnapPreventionEdgeFilter;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.routing.weighting.TDWeighting;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.*;
import com.graphhopper.util.Parameters.Routing;
import com.graphhopper.util.exceptions.PointNotFoundException;
import com.graphhopper.util.shapes.GHPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.graphhopper.util.EdgeIterator.ANY_EDGE;
import static com.graphhopper.util.EdgeIterator.NO_EDGE;
import static com.graphhopper.util.Parameters.Curbsides.CURBSIDE_ANY;
import static com.graphhopper.util.Parameters.Routing.CURBSIDE;

/**
 * Implementation of calculating a route with multiple via points.
 *
 * @author Peter Karich
 */
public class ViaRoutingTemplate extends AbstractRoutingTemplate implements RoutingTemplate {
    protected final GHRequest ghRequest;
    protected final GHResponse ghResponse;
    // result from route
    protected List<Path> pathList;
    protected final ResponsePath responsePath = new ResponsePath();
    private final EnumEncodedValue<RoadClass> roadClassEnc;
    private final EnumEncodedValue<RoadEnvironment> roadEnvEnc;

    public ViaRoutingTemplate(GHRequest ghRequest, GHResponse ghRsp, LocationIndex locationIndex,
                              EncodedValueLookup lookup, final Weighting weighting) {
        super(locationIndex, lookup, weighting);
        this.ghRequest = ghRequest;
        this.ghResponse = ghRsp;
        this.roadClassEnc = lookup.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
        this.roadEnvEnc = lookup.getEnumEncodedValue(RoadEnvironment.KEY, RoadEnvironment.class);
    }

    @Override
    public List<QueryResult> lookup(List<GHPoint> points) {
        if (points.size() < 2)
            throw new IllegalArgumentException("At least 2 points have to be specified, but was:" + points.size());

        EdgeFilter strictEdgeFilter = !ghRequest.hasSnapPreventions()
                ? edgeFilter
                : new SnapPreventionEdgeFilter(edgeFilter, roadClassEnc, roadEnvEnc, ghRequest.getSnapPreventions());
        queryResults = new ArrayList<>(points.size());
        for (int placeIndex = 0; placeIndex < points.size(); placeIndex++) {
            GHPoint point = points.get(placeIndex);
            QueryResult qr = null;
            if (ghRequest.hasPointHints())
                qr = locationIndex.findClosest(point.lat, point.lon, new NameSimilarityEdgeFilter(strictEdgeFilter,
                        ghRequest.getPointHints().get(placeIndex), point, 100));
            else if (ghRequest.hasSnapPreventions())
                qr = locationIndex.findClosest(point.lat, point.lon, strictEdgeFilter);
            if (qr == null || !qr.isValid())
                qr = locationIndex.findClosest(point.lat, point.lon, edgeFilter);
            if (!qr.isValid())
                ghResponse.addError(new PointNotFoundException("Cannot find point " + placeIndex + ": " + point, placeIndex));

            queryResults.add(qr);
        }

        return queryResults;
    }

    @Override
    public List<Path> calcPaths(QueryGraph queryGraph, RoutingAlgorithmFactory algoFactory, AlgorithmOptions algoOpts) {
        long visitedNodesSum = 0L;
        final boolean viaTurnPenalty = ghRequest.getHints().getBool(Routing.PASS_THROUGH, false);
        final int pointsCount = ghRequest.getPoints().size();
        pathList = new ArrayList<>(pointsCount - 1);

        List<DirectionResolverResult> directions = Collections.emptyList();
        if (!ghRequest.getCurbsides().isEmpty()) {
            DirectionResolver directionResolver = new DirectionResolver(queryGraph, accessEnc);
            directions = new ArrayList<>(queryResults.size());
            for (QueryResult qr : queryResults) {
                directions.add(directionResolver.resolveDirections(qr.getClosestNode(), qr.getQueryPoint()));
            }
        }

        QueryResult fromQResult = queryResults.get(0);
        StopWatch sw;
        for (int placeIndex = 1; placeIndex < pointsCount; placeIndex++) {
            if (placeIndex == 1) {
                // enforce start direction
                double initialHeading = ghRequest.getHeadings().isEmpty() ? Double.NaN : ghRequest.getHeadings().get(0);
                queryGraph.enforceHeading(fromQResult.getClosestNode(), initialHeading, false);
            } else if (viaTurnPenalty) {
                // enforce straight start after via stop
                Path prevRoute = pathList.get(placeIndex - 2);
                if (prevRoute.getEdgeCount() > 0) {
                    EdgeIteratorState incomingVirtualEdge = prevRoute.getFinalEdge();
                    queryGraph.unfavorVirtualEdgePair(fromQResult.getClosestNode(), incomingVirtualEdge.getEdge());
                }
            }

            QueryResult toQResult = queryResults.get(placeIndex);

            // enforce end direction
            double heading = ghRequest.getPoints().size() == ghRequest.getHeadings().size() ? ghRequest.getHeadings().get(placeIndex) : Double.NaN;
            queryGraph.enforceHeading(toQResult.getClosestNode(), heading, true);

            sw = new StopWatch().start();
            RoutingAlgorithm algo = algoFactory.createAlgo(queryGraph, algoOpts);
            String debug = ", algoInit:" + sw.stop().getSeconds() + "s";

            sw = new StopWatch().start();

            // calculate paths
            List<Path> tmpPathList;
            if (algoOpts.getWeighting() instanceof TDWeighting) {
                int departure_time = ghRequest.getHints().getInt("departure_time", -1);
                if (departure_time == -1) {
                    throw new RuntimeException("Must specify departure_time in request.");
                }
                ((TDWeighting) algoOpts.getWeighting()).setInitialTime(departure_time * 1000);
                tmpPathList = ((AbstractRoutingAlgorithm) algo).calcTDPaths(fromQResult.getClosestNode(), toQResult.getClosestNode(), departure_time * 1000);
            } else {
                tmpPathList = algo.calcPaths(fromQResult.getClosestNode(), toQResult.getClosestNode());
            }
            debug += ", " + algo.getName() + "-routing:" + sw.stop().getSeconds() + "s";
            if (tmpPathList.isEmpty())
                throw new IllegalStateException("At least one path has to be returned for " + fromQResult + " -> " + toQResult);

            // todo: can tmpPathList ever have more than one path here? and would it even be correct to add them all
            // to pathList then?
            for (int i = 0; i < tmpPathList.size(); i++) {
                Path path = tmpPathList.get(i);
                if (path.getTime() < 0)
                    throw new RuntimeException("Time was negative " + path.getTime() + " for index " + i + ". Please report as bug and include:" + ghRequest);

                pathList.add(path);
                debug += ", " + path.getDebugInfo();
            }

            responsePath.addDebugInfo(debug);

            // reset all direction enforcements in queryGraph to avoid influencing next path
            queryGraph.clearUnfavoredStatus();

            if (algo.getVisitedNodes() >= algoOpts.getMaxVisitedNodes())
                throw new IllegalArgumentException("No path found due to maximum nodes exceeded " + algoOpts.getMaxVisitedNodes());

            visitedNodesSum += algo.getVisitedNodes();
            responsePath.addDebugInfo("visited nodes sum: " + visitedNodesSum);
            fromQResult = toQResult;
        }

        ghResponse.getHints().putObject("visited_nodes.sum", visitedNodesSum);
        ghResponse.getHints().putObject("visited_nodes.average", (float) visitedNodesSum / (pointsCount - 1));

        return pathList;
    }

    private int ignoreThrowOrAcceptImpossibleCurbsides(int edge, int placeIndex, boolean forceCurbsides) {
        if (edge != NO_EDGE) {
            return edge;
        }
        if (forceCurbsides) {
            return throwImpossibleCurbsideConstraint(placeIndex);
        } else {
            return ANY_EDGE;
        }
    }

    private int throwImpossibleCurbsideConstraint(int placeIndex) {
        throw new IllegalArgumentException("Impossible curbside constraint: 'curbside=" + ghRequest.getCurbsides().get(placeIndex) + "' at point " + placeIndex);
    }

    @Override
    public void finish(PathMerger pathMerger, Translation tr) {
        if (ghRequest.getPoints().size() - 1 != pathList.size())
            throw new RuntimeException("There should be exactly one more points than paths. points:" + ghRequest.getPoints().size() + ", paths:" + pathList.size());

        responsePath.setWaypoints(getWaypoints());
        ghResponse.add(responsePath);
        pathMerger.doWork(responsePath, pathList, lookup, tr);
    }

}
