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

import Leg, { LegMode } from "./Leg.js";

export default class PtLeg extends Leg {
  get isDelayed() {
    return this._isDelayed;
  }

  constructor(apiLeg) {
    super(apiLeg, LegMode.PT);
  }

  initializeTurns(apiLeg) {
    this._geometry = {
      type: "LineString",
      coordinates: apiLeg.getStopsList().map(stop => [stop.getPoint().getLon(), stop.getPoint().getLat()])
    };
    let result = [];
    console.log(apiLeg)
    //leave out first and last stop since they are displayed as waypoint
    for (let i = 1; i < apiLeg.getStopsList().length - 1; i++) {
      const apiStop = apiLeg.getStopsList()[i];
      let stop = {
        name: apiStop.getStopName(),
        departureTime: apiStop.getDepartureTime().toDate(),
        delay: this._calculateDelay(
          apiStop.getDepartureTime().toDate(),
          apiStop.getDepartureTime().toDate()
        ),
        isCancelled: false,
        geometry: {
          type: "Point",
          coordinates: [apiStop.getPoint().getLon(), apiStop.getPoint().getLat()]
        }
      };
      result.push(stop);
    }
    return result;
  }

  initializeDistance(apiLeg) {
    //The first stop is where passengers enter thus n - 1 stops to go on this leg
    return apiLeg.getStopsList().length - 1 + " Stops";
  }

  _calculateDelay(actual, planned) {
    if (!actual || !planned) {
      return 0;
    }

    let actualTime = moment(actual);
    let plannedTime = moment(planned);
    let diff = actualTime.diff(plannedTime, "minutes");
    return diff;
  }
}
