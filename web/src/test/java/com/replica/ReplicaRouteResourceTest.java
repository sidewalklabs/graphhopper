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
package com.replica;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Preconditions;
import com.graphhopper.GHResponse;
import com.graphhopper.config.Profile;
import com.graphhopper.http.GraphHopperApplication;
import com.graphhopper.http.GraphHopperBundle;
import com.graphhopper.http.GraphHopperServerConfiguration;
import com.graphhopper.http.cli.GtfsLinkMapperCommand;
import com.graphhopper.http.cli.ImportCommand;
import com.graphhopper.resources.InfoResource;
import com.graphhopper.util.Helper;
import io.dropwizard.Configuration;
import io.dropwizard.cli.Cli;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.util.JarLocation;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.io.File;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests the entire app, not the resource, so that the plugging-together
 * of stuff (which is different for PT than for the rest) is under test, too.
 */
@ExtendWith(DropwizardExtensionsSupport.class)
public class ReplicaRouteResourceTest {
    private static final String TARGET_DIR = "./target/gtfs-app-gh/";
    private static final String TRANSIT_DATA_DIR = "transit_data/";
    public static final DropwizardAppExtension<GraphHopperServerConfiguration> app = new DropwizardAppExtension<>(GraphHopperApplication.class, createConfig());

    private static GraphHopperServerConfiguration createConfig() {
        GraphHopperServerConfiguration config = new GraphHopperServerConfiguration();
        config.getGraphHopperConfiguration().
                putObject("graph.flag_encoders", "foot").
                putObject("datareader.file", "test-data/beatty.osm").
                putObject("gtfs.file", "test-data/sample-feed.zip").
                putObject("graph.location", TARGET_DIR).
                setProfiles(Collections.singletonList(new Profile("foot").setVehicle("foot").setWeighting("fastest")));
        return config;
    }

    @BeforeAll
    public static void setUp() {
        // Fresh target directory
        Helper.removeDir(new File(TARGET_DIR));
        // Create new empty directory for GTFS/OSM resources
        File transitDataDir = new File(TRANSIT_DATA_DIR);
        if (transitDataDir.exists()) {
            throw new IllegalStateException(TRANSIT_DATA_DIR + " directory should not already exist.");
        }
        Preconditions.checkState(transitDataDir.mkdir(), "could not create directory " + TRANSIT_DATA_DIR);

        // Setup necessary mock
        final JarLocation location = mock(JarLocation.class);
        when(location.getVersion()).thenReturn(Optional.of("1.0.0"));

        // Add commands you want to test
        final Bootstrap<GraphHopperServerConfiguration> bootstrap = new Bootstrap<>(new GraphHopperApplication());
        bootstrap.addBundle(new GraphHopperBundle());
        bootstrap.addCommand(new ImportCommand());
        bootstrap.addCommand(new GtfsLinkMapperCommand());

        // Build what'll run the command and interpret arguments
        Cli cli = new Cli(location, bootstrap, System.out, System.err);
        cli.run("import", "test-data/beatty-sample-feed-config.yml");
        cli.run("gtfs_links", "test-data/beatty-sample-feed-config.yml");
    }

    @AfterAll
    public static void cleanUp() {
        Helper.removeDir(new File(TARGET_DIR));
        Helper.removeDir(new File(TRANSIT_DATA_DIR));
    }

    @Test
    public void testStationStationQuery() {
        final Response response = clientTarget(app, "/route")
                .queryParam("point", "Stop(NADAV)")
                .queryParam("point", "Stop(NANAA)")
                .queryParam("vehicle", "pt")
                .queryParam("pt.earliest_departure_time", "2007-01-01T08:00:00Z")
                .request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        GHResponse ghResponse = response.readEntity(GHResponse.class);
        assertFalse(ghResponse.hasErrors());
    }

    @Test
    public void testPointPointQuery() {
        final Response response = clientTarget(app, "/route-pt")
                .queryParam("point", "36.914893,-116.76821") // NADAV stop
                .queryParam("point", "36.914944,-116.761472") //NANAA stop
                .queryParam("vehicle", "pt")
                .queryParam("pt.earliest_departure_time", "2007-01-01T08:00:00Z")
                .request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        GHResponse ghResponse = response.readEntity(GHResponse.class);
        assertFalse(ghResponse.hasErrors());
    }

    @Test
    public void testWalkQuery() {
        final Response response = clientTarget(app, "/route")
                .queryParam("point", "36.914893,-116.76821")
                .queryParam("point", "36.914944,-116.761472")
                .queryParam("profile", "foot")
                .request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        GHResponse ghResponse = response.readEntity(GHResponse.class);
        assertFalse(ghResponse.hasErrors());
    }

    @Test
    public void testNoPoints() {
        final Response response = clientTarget(app, "/route")
                .queryParam("vehicle", "pt")
                .request().buildGet().invoke();
        assertEquals(400, response.getStatus());
    }

    @Test
    public void testOnePoint() {
        final Response response = clientTarget(app, "/route")
                .queryParam("point", "36.914893,-116.76821")
                .queryParam("vehicle", "pt")
                .queryParam("pt.earliest_departure_time", "2007-01-01T08:00:00Z")
                .request().buildGet().invoke();
        assertEquals(400, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        assertEquals("query param point size must be between 2 and 2", json.get("message").asText());
    }

    @Test
    public void testBadPoints() {
        final Response response = clientTarget(app, "/route")
                .queryParam("point", "pups")
                .queryParam("vehicle", "pt")
                .queryParam("pt.earliest_departure_time", "2007-01-01T08:00:00Z")
                .request().buildGet().invoke();
        assertEquals(400, response.getStatus());
    }

    @Test
    public void testNoTime() {
        final Response response = clientTarget(app, "/route")
                .queryParam("point", "36.914893,-116.76821") // NADAV stop
                .queryParam("point", "36.914944,-116.761472") //NANAA stop
                .queryParam("vehicle", "pt")
                .request().buildGet().invoke();
        assertEquals(400, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        // Would prefer a "must not be null" message here, but is currently the same as for a bad time (see below).
        // I DO NOT want to manually catch this, I want to figure out how to fix this upstream, or live with it.
        assertTrue(json.get("message").asText().startsWith("query param pt.earliest_departure_time must"));
    }

    @Test
    public void testBadTime() {
        final Response response = clientTarget(app, "/route")
                .queryParam("point", "36.914893,-116.76821") // NADAV stop
                .queryParam("point", "36.914944,-116.761472") //NANAA stop
                .queryParam("vehicle", "pt")
                .queryParam("pt.earliest_departure_time", "wurst")
                .request().buildGet().invoke();
        assertEquals(400, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        assertEquals("query param pt.earliest_departure_time must be in a ISO-8601 format.", json.get("message").asText());
    }

    @Test
    public void testInfo() {
        final Response response = clientTarget(app, "/info")
                .request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        InfoResource.Info info = response.readEntity(InfoResource.Info.class);
        assertTrue(info.supported_vehicles.contains("pt"));
        assertTrue(info.profiles.stream().anyMatch(p -> p.name.equals("pt")));
    }

    public static WebTarget clientTarget(DropwizardAppExtension<? extends Configuration> app, String path) {
        path = prefixPathWithSlash(path);
        return app.client().target(clientUrl(app, path));
    }

    public static String clientUrl(DropwizardAppExtension<? extends Configuration> app, String path) {
        path = prefixPathWithSlash(path);
        return "http://localhost:" + app.getLocalPort() + path;
    }

    private static String prefixPathWithSlash(String path) {
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return path;
    }

}
