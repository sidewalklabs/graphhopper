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

package com.graphhopper.http.cli;

import com.graphhopper.CustomGraphHopperOSM;
import com.graphhopper.GraphHopper;
import com.graphhopper.CustomGraphHopperGtfs;
import com.graphhopper.http.GraphHopperManaged;
import com.graphhopper.http.GraphHopperServerConfiguration;
import com.graphhopper.replica.OsmHelper;
import com.graphhopper.replica.StableEdgeIdManager;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;

public class ImportCommand extends ConfiguredCommand<GraphHopperServerConfiguration> {

    private boolean localTest;

    public ImportCommand() {
        super("import", "creates the graphhopper files used for later (faster) starts");
        this.localTest = false;
    }

    public ImportCommand(boolean localTest) {
        super("import", "creates the graphhopper files used for later (faster) starts");
        this.localTest = localTest;
    }

    @Override
    protected void run(Bootstrap<GraphHopperServerConfiguration> bootstrap, Namespace namespace, GraphHopperServerConfiguration configuration) {
        final GraphHopperManaged graphHopper = new GraphHopperManaged(configuration.getGraphHopperConfiguration(), bootstrap.getObjectMapper());
        GraphHopper gh = graphHopper.getGraphHopper();
        gh.importOrLoad();
        if (gh instanceof CustomGraphHopperGtfs) {
            CustomGraphHopperGtfs customGh = (CustomGraphHopperGtfs) gh;
            customGh.collectOsmInfo();
            if (this.localTest) {
                OsmHelper.writeOsmInfoToMapDb(customGh);
            } else {
                OsmHelper.writeOsmInfoToMapDb(customGh, "test-data/osm_info.db");
            }
        } else {
            CustomGraphHopperOSM customGh = (CustomGraphHopperOSM) gh;
            customGh.collectOsmInfo();
            if (this.localTest) {
                OsmHelper.writeOsmInfoToMapDb(customGh);
            } else {
                OsmHelper.writeOsmInfoToMapDb(customGh, "test-data/osm_info.db");
            }
        }
        StableEdgeIdManager stableEdgeIdManager = new StableEdgeIdManager(gh);
        stableEdgeIdManager.setStableEdgeIds();
        gh.close();
    }
}
