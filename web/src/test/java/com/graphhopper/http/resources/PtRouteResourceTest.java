package com.graphhopper.http.resources;/*
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

import com.graphhopper.GHResponse;
import com.graphhopper.config.Profile;
import com.graphhopper.http.GraphHopperApplication;
import com.graphhopper.http.GraphHopperServerConfiguration;
import com.graphhopper.util.Helper;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.Ignore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.ws.rs.core.Response;
import java.io.File;
import java.util.Collections;

import static com.graphhopper.http.resources.TestUtils.clientTarget;

/**
 * Tests the entire app, not the resource, so that the plugging-together
 * of stuff (which is different for PT than for the rest) is under test, too.
 */
@ExtendWith(DropwizardExtensionsSupport.class)
public class PtRouteResourceTest {
    private static final String DIR = "./target/gtfs-app-gh/";
    public static final DropwizardAppExtension<GraphHopperServerConfiguration> app = new DropwizardAppExtension<>(GraphHopperApplication.class, createConfig());

    private static GraphHopperServerConfiguration createConfig() {
        GraphHopperServerConfiguration config = new GraphHopperServerConfiguration();
        config.getGraphHopperConfiguration().
                putObject("graph.flag_encoders", "foot").
                putObject("datareader.file", "test-data/beatty.osm").
                putObject("gtfs.file", "test-data/sample-feed.zip,test-data/another-sample-feed.zip").
                putObject("graph.location", DIR).
                setProfiles(Collections.singletonList(new Profile("foot").setVehicle("foot").setWeighting("fastest")));
        return config;
    }

    @BeforeAll
    @AfterAll
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    @Test @Ignore
    public void testStationStationQuery() {
        final Response response = clientTarget(app, "/route")
                .queryParam("point", "Stop(JUSTICE_COURT)")
                .queryParam("point", "Stop(DADAN)")
                .queryParam("vehicle", "pt")
                .queryParam("pt.earliest_departure_time", "2007-01-01T08:00:00Z")
                .queryParam("details", "r5_edge_id")
                .request().buildGet().invoke();
        Assertions.assertEquals(200, response.getStatus());
        GHResponse ghResponse = response.readEntity(GHResponse.class);
        Assertions.assertFalse(ghResponse.hasErrors());
    }

}
