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

import com.google.common.collect.Lists;
import com.graphhopper.*;
import com.graphhopper.gtfs.GHLocation;
import com.graphhopper.gtfs.GHPointLocation;
import com.graphhopper.gtfs.PtRouter;
import com.graphhopper.gtfs.Request;
import com.graphhopper.util.PMap;
import com.graphhopper.util.shapes.GHPoint;
import io.grpc.stub.StreamObserver;
import router.RouterOuterClass;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static com.graphhopper.util.Parameters.Routing.INSTRUCTIONS;

public class RouterImpl extends router.RouterGrpc.RouterImplBase {

    private final GraphHopper graphHopper;
    private final PtRouter ptRouter;

    public RouterImpl(GraphHopper graphHopper, PtRouter ptRouter) {
        this.graphHopper = graphHopper;
        this.ptRouter = ptRouter;
    }

    @Override
    public void route(RouterOuterClass.RouteRequest request, StreamObserver<RouterOuterClass.RouteReply> responseObserver) {
        if (request.getProfile().equals("pt")) {
            routePt(request, responseObserver);
        } else {
            routeStreetMode(request, responseObserver);
        }
    }

    private void routeStreetMode(RouterOuterClass.RouteRequest request, StreamObserver<RouterOuterClass.RouteReply> responseObserver) {
        GHRequest ghRequest = new GHRequest(
                request.getPointsList().stream().map(p -> new GHPoint(p.getLat(), p.getLon())).collect(Collectors.toList())
        );
        // Always return stable edge IDs, even if they aren't requested
        ghRequest.setPathDetails(Lists.newArrayList("stable_edge_ids"));
        ghRequest.setProfile(request.getProfile());
        ghRequest.setLocale(Locale.US);

        PMap hints = new PMap();
        hints.putObject(INSTRUCTIONS, false);
        /*
        if (algo == alternative) {


            hints.putObject("alternative_route.max_paths", );
            hints.putObject("alternative_route.max_weight_factor", );
            hints.putObject("alternative_route.max_share_factor", );
            ghRequest.getHints().putAll(hints);
        }
         */
        GHResponse ghResponse = graphHopper.route(ghRequest);

        RouterOuterClass.RouteReply.Builder replyBuilder = RouterOuterClass.RouteReply.newBuilder();
        for (ResponsePath responsePath : ghResponse.getAll()) {
            List<RouterOuterClass.Leg> legs = Lists.newArrayList();

            // todo: add remaining pt/foot specific fields
            for (Trip.Leg leg : responsePath.getLegs()) {
                if (leg.type.equals("pt")) {
                    legs.add(RouterOuterClass.Leg.newBuilder()
                            .setType("pt")
                            .setArrivalTime(leg.getArrivalTime().getTime())
                            .setDepartureTime(leg.getDepartureTime().getTime())
                            .build());
                } else {
                    legs.add(RouterOuterClass.Leg.newBuilder()
                            .setType("foot")
                            .setArrivalTime(leg.getArrivalTime().getTime())
                            .setDepartureTime(leg.getDepartureTime().getTime())
                            .build());
                }
            }
            List<String> pathStableEdgeIds = responsePath.getPathDetails().get("stable_edge_ids").stream()
                    .map(pathDetail -> (String) pathDetail.getValue())
                    .collect(Collectors.toList());

            replyBuilder.addPaths(RouterOuterClass.Path.newBuilder()
                    .setTime(responsePath.getTime())
                    .setDistance(responsePath.getDistance())
                    .addAllLegs(legs)
                    .addAllStableEdgeIds(pathStableEdgeIds)
            );
        }
        responseObserver.onNext(replyBuilder.build());
        responseObserver.onCompleted();
    }

    private void routePt(RouterOuterClass.RouteRequest request, StreamObserver<RouterOuterClass.RouteReply> responseObserver) {
        Instant when = null;
        List<GHLocation> collect = request.getPointsList().stream().map(p -> new GHPointLocation(new GHPoint(p.getLat(), p.getLon()))).collect(Collectors.toList());
        Request ptRequest = new Request(collect, when);
        GHResponse ghResponse = ptRouter.route(ptRequest);
        // ...
    }

}
