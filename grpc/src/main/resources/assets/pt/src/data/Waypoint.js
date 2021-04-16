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

import { LegMode as Mode } from "./Leg.js";

export default class Waypoint {
  get prevMode() {
    return this._prevMode;
  }

  get arrivalTime() {
    return this._arrivalTime;
  }

  get arrivalDelay() {
    return this._arrivalDelay;
  }

  get nextMode() {
    return this._nextMode;
  }

  get departureTime() {
    return this._departureTime;
  }

  get departureDelay() {
    return this._departureDelay;
  }

  get name() {
    return this._name;
  }

  get geometry() {
    return this._geometry;
  }

  get isPossible() {
    return this._isPossible;
  }

  get type() {
    return this._type;
  }

  constructor(prevApiLeg, nextApiLeg) {
    this._departureDelay = 0;
    this._type = WaypointType.INBEETWEEN;
    this._isPossible = true;
    this._initialize(prevApiLeg, nextApiLeg);
    this._checkIfPossible(prevApiLeg, nextApiLeg);
  }

  _randomDelay() {
    let delay = (Math.random() - 0.5) * 100;
    return Math.round(delay);
  }

  _initialize(prevApiLeg, nextApiLeg) {
    this._initializeName(prevApiLeg, nextApiLeg);
    this._initializeMode(prevApiLeg, nextApiLeg);
    this._initializeDepartureTime(nextApiLeg);
    this._initializeWaypointType(prevApiLeg, nextApiLeg);
    this._initializeArrivalTime(prevApiLeg);
    this._initializeGeometry(prevApiLeg, nextApiLeg, this._type);
  }

  _initializeName(prevApiLeg, nextApiLeg) {
    if (nextApiLeg) {
      if (nextApiLeg.type === Mode.PT) {
        this._name = nextApiLeg.getAgencyName() + ":" + nextApiLeg.getRouteId() + ":" + nextApiLeg.getRouteShortName() + ":" + nextApiLeg.getRouteLongName() + " @ " + nextApiLeg.getStopsList()[0].getStopName();
      } else if (prevApiLeg) {
        this._name = this._findArrivalLocation(prevApiLeg);
      } else {
        this._name = this._findWalkLocation(nextApiLeg, false);
      }
    } else if (prevApiLeg) {
      this._name = this._findWalkLocation(prevApiLeg, true);
    }
  }

  _initializeMode(prevApiLeg, nextApiLeg) {
    if (prevApiLeg) this._prevMode = prevApiLeg.type;
    if (nextApiLeg) this._nextMode = nextApiLeg.type;
  }

  _initializeWaypointType(prevApiLeg, nextApiLeg) {
    this._type = WaypointType.INBEETWEEN;
    if (!prevApiLeg) {
      this._type = WaypointType.FIRST;
    }
    if (!nextApiLeg) {
      this._type = WaypointType.LAST;
    }
  }

  _initializeArrivalTime(apiLeg) {
    if (!apiLeg) return;
    if (apiLeg.type === Mode.PT) {
      let lastStop = apiLeg.getStopsList()[apiLeg.getStopsList().length - 1];
      this._arrivalTime = lastStop.getArrivalTime().toDate();
      this._arrivalDelay = this._calculateDelay(
        lastStop.getArrivalTime().toDate(),
        this._arrivalTime
      );
      this._isArrivalCancelled = false;
    } else {
      this._arrivalTime = apiLeg.getArrivalTime().toDate();
    }
  }

  _initializeDepartureTime(apiLeg) {
    if (!apiLeg) return;
    if (apiLeg.type === Mode.PT) {
      let firstStop = apiLeg.getStopsList()[0];
      this._departureTime = firstStop.getDepartureTime().toDate();
      this._departureDelay = this._calculateDelay(
        firstStop.getDepartureTime().toDate(),
        this._departureTime
      );
      this._isDepartureCancelled = false;
    } else {
      this._departureTime = apiLeg.getDepartureTime().toDate();
    }
  }

  _initializeGeometry(prevApiLeg, nextApiLeg, waypointType) {
    if (waypointType === WaypointType.INBEETWEEN) {
      if (nextApiLeg.type === Mode.PT) {
        let result = nextApiLeg.getStopsList()[0].getPoint();
        this._geometry = {
          type: "Point",
          coordinates: [result.getLon(), result.getLat()]
        };
      } else if (prevApiLeg) {
        let result = prevApiLeg.getStopsList()[prevApiLeg.getStopsList().length - 1].getPoint();
        this._geometry = {
          type: "Point",
          coordinates: [result.getLon(), result.getLat()]
        };
      }
    }
  }

  _findArrivalLocation(apiLeg) {
    return apiLeg.getStopsList()[apiLeg.getStopsList().length - 1].getStopName();
  }

  _findWalkLocation(apiLeg, isArrival) {
    return "walk";
  }

  _checkIfPossible(prevApiLeg, nextApiLeg) {
    if (this._isArrivalCancelled || this._isDepartureCancelled) {
      this._isPossible = false;
    }

    if (
      this._isPossible &&
      prevApiLeg &&
      nextApiLeg &&
      prevApiLeg.type === Mode.PT &&
      nextApiLeg.type === Mode.PT
    ) {
      let buffer = moment(this.departureTime)
        .add(this.departureDelay, "minutes")
        .diff(moment(this.arrivalTime).add(this.arrivalDelay, "minutes"));
      this._isPossible = buffer >= 0;
    }
  }

  _calculateDelay(actual, planned) {
    return moment(actual).diff(moment(planned), "minutes");
  }
}

export const WaypointType = {
  FIRST: "WaypointType_FIRST",
  LAST: "WaypointType_LAST",
  INBEETWEEN: "WaypointType_INBEETWEEN"
};
