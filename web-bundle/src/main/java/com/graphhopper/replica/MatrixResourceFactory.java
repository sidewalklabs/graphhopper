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

package com.graphhopper.replica;

import com.graphhopper.GraphHopperConfig;
import com.graphhopper.matrix.http.MatrixResource;
import com.graphhopper.matrix.model.MatrixQueue;
import com.graphhopper.matrix.model.MatrixSerializer;
import com.graphhopper.routing.MatrixAPI;
import com.graphhopper.routing.ProfileResolver;

import javax.inject.Inject;
import javax.ws.rs.Path;

@Path("/matrix")
public class MatrixResourceFactory {

    private final GraphHopperConfig graphHopperBundleConfiguration;
    private final MatrixSerializer serializer;
    private final MatrixAPI matrixAPI;
    private final MatrixQueue matrixQueue;
    private final ProfileResolver profileResolver;

    @Inject
    public MatrixResourceFactory(GraphHopperConfig graphHopperBundleConfiguration, ProfileResolver profileResolver, MatrixSerializer serializer, MatrixAPI matrixAPI, MatrixQueue matrixQueue) {
        this.graphHopperBundleConfiguration = graphHopperBundleConfiguration;
        this.profileResolver = profileResolver;
        this.serializer = serializer;
        this.matrixAPI = matrixAPI;
        this.matrixQueue = matrixQueue;
    }

    @Path("/")
    public MatrixResource getMatrix() {
        return new MatrixResource(
                graphHopperBundleConfiguration,
                profileResolver,
                matrixAPI, matrixQueue, serializer);
    }

}
