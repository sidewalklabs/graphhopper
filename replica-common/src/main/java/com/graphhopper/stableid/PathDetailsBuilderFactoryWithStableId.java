package com.graphhopper.stableid;/*
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

import com.graphhopper.coll.MapEntry;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.details.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.graphhopper.routing.util.EncodingManager.getKey;
import static com.graphhopper.util.Parameters.Details.*;

public class PathDetailsBuilderFactoryWithStableId extends PathDetailsBuilderFactory {

    @Override
    public List<PathDetailsBuilder> createPathDetailsBuilders(List<String> requestedPathDetails, EncodedValueLookup evl, Weighting weighting) {
        // request-scoped
        List<PathDetailsBuilder> builders = new ArrayList<>();

        if (requestedPathDetails.contains(AVERAGE_SPEED))
            builders.add(new AverageSpeedDetails(weighting));

        if (requestedPathDetails.contains(STREET_NAME))
            builders.add(new StreetNameDetails());

        if (requestedPathDetails.contains(EDGE_ID))
            builders.add(new EdgeIdDetails());

        if (requestedPathDetails.contains(TIME))
            builders.add(new TimeDetails(weighting));

        if (requestedPathDetails.contains(WEIGHT))
            builders.add(new WeightDetails(weighting));

        if (requestedPathDetails.contains(DISTANCE))
            builders.add(new DistanceDetails());

        for (String checkSuffix : requestedPathDetails) {
            if (checkSuffix.contains(getKey("", "priority")) && evl.hasEncodedValue(checkSuffix))
                builders.add(new DecimalDetails(checkSuffix, evl.getDecimalEncodedValue(checkSuffix)));
        }

        for (String key : Arrays.asList(MaxSpeed.KEY, MaxWidth.KEY, MaxHeight.KEY, MaxWeight.KEY,
                MaxAxleLoad.KEY, MaxLength.KEY)) {
            if (requestedPathDetails.contains(key) && evl.hasEncodedValue(key))
                builders.add(new DecimalDetails(key, evl.getDecimalEncodedValue(key)));
        }

        if (requestedPathDetails.contains("edge_key")) {
            builders.add(new EdgeKeyDetails());
        }

        if (requestedPathDetails.contains("stable_edge_ids")) {
            builders.add(new StableIdPathDetailsBuilder(evl));
        }

        for (Map.Entry entry : Arrays.asList(new MapEntry<>(RoadClass.KEY, RoadClass.class),
                new MapEntry<>(RoadEnvironment.KEY, RoadEnvironment.class), new MapEntry<>(Surface.KEY, Surface.class),
                new MapEntry<>(RoadAccess.KEY, RoadAccess.class), new MapEntry<>(Toll.KEY, Toll.class),
                new MapEntry<>(TrackType.KEY, TrackType.class), new MapEntry<>(Hazmat.KEY, Hazmat.class),
                new MapEntry<>(HazmatTunnel.KEY, HazmatTunnel.class), new MapEntry<>(HazmatWater.KEY, HazmatWater.class),
                new MapEntry<>(Country.KEY, Country.class))) {
            String key = (String) entry.getKey();
            if (requestedPathDetails.contains(key) && evl.hasEncodedValue(key))
                builders.add(new EnumDetails(key, evl.getEnumEncodedValue(key, (Class<Enum>) entry.getValue())));
        }

        if (requestedPathDetails.size() != builders.size()) {
            throw new IllegalArgumentException("You requested the details " + requestedPathDetails + " but we could only find " + builders);
        }

        return builders;
    }
}
