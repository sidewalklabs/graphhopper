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

import Sidebar from "./sidebar/Sidebar.js";
import Map from "./map/Map.js";
import {CreateQuery, ParseQuery, TimeOption} from "../data/Query.js";
import Path from "../data/Path.js";
import * as timestamp_pb from 'google-protobuf/google/protobuf/timestamp_pb';
import Router from "../grpc/grpc/src/main/proto/router_grpc_web_pb.js";

export default class App extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            info: null,
            from: null,
            to: null,
            departureDateTime: new moment(),
            limitSolutions: 4,
            maxProfileDuration: 10,
            betaWalkTime: 1.5,
            limitStreetTimeSeconds: 1440,
            usePareto: false,
            betaTransfers: 1440000.0,
            routes: {
                query: null,
                isFetching: false
            }
        };
        ParseQuery(this.state, new URL(window.location).searchParams);
    }

    componentDidMount() {
        var router = new Router.RouterClient('/api');
        var component = this;
        router.info(new Router.InfoRequest(), null, function(err, response) {
            if (err) {
                console.log("Error in Webrequest. Code: " + err.code)
            } else {
                console.log(response.toObject())
                component.setState({info: {
                    bbox: response.getBboxList()
                }})
            }
        });
    }

    componentDidUpdate(prevProps, prevState) {
        if (this.state.info !== null) { // Maybe better: Create a wrapper component that only renders this one when info is ready
            if (this.state.from !== null && this.state.to !== null) { // The only ways our state would not correspond to a valid query
                let query = CreateQuery(new URL("/route", window.location), this.state);
                let appQuery = CreateQuery(window.location, this.state);
                if (this.state.routes.query !== query) {
                    // What we are currently seeing or fetching is not want we want to see.
                    // So we make a request.
                    console.log(query);
                    this.setState({
                        routes: {
                            query: query,
                            isFetching: true
                        }
                    });
                    var ptRouteRequest = new Router.PtRouteRequest();
                    var from = new Router.Point();
                    from.setLat(this.state.from.lat);
                    from.setLon(this.state.from.long);
                    console.log(this.state.from);
                    var to = new Router.Point();
                    to.setLat(this.state.to.lat);
                    to.setLon(this.state.to.long);
                    ptRouteRequest.addPoints(from);
                    ptRouteRequest.addPoints(to);
                    const timestampFromDate = timestamp_pb.Timestamp.fromDate(this.state.departureDateTime.toDate());
                    ptRouteRequest.setEarliestDepartureTime(timestampFromDate);
                    ptRouteRequest.setLimitSolutions(this.state.limitSolutions);
                    ptRouteRequest.setMaxProfileDuration(this.state.maxProfileDuration);
                    ptRouteRequest.setBetaWalkTime(this.state.betaWalkTime);
                    ptRouteRequest.setLimitStreetTimeSeconds(this.state.limitStreetTimeSeconds)
                    ptRouteRequest.setUsePareto(this.state.usePareto);
                    ptRouteRequest.setBetaTransfers(this.state.betaTransfers);
                    var component = this;
                    var router = new Router.RouterClient('/api');
                    router.routePt(ptRouteRequest, null, function(err, response) {
                        if (err) {
                            console.log(err);
                            component.setState({
                                routes: {
                                    query: query,
                                    isFetching: false,
                                    isLastQuerySuccess: false
                                }
                            });
                        } else {
                            component.setState(prevState => {
                                if (CreateQuery(new URL("/route", window.location), prevState) !== query) return {}; // This reply is not what we want to know anymore
                                console.log(response.toObject());
                                const paths = response.getPathsList().map(path => new Path(path));
                                const selectedPath = component._selectPathOnReceive(paths);
                                return {
                                    routes: {
                                        query: query,
                                        paths: paths,
                                        isLastQuerySuccess: true,
                                        isFetching: false,
                                        selectedRouteIndex: selectedPath
                                    }
                                };
                            });
                        }
                    })
                    window.history.replaceState({
                        name: "last state"
                    }, "", appQuery);
                }
            }
        }
    }

    _selectPathOnReceive(paths) {
        for (let i = 0; i < paths.length; i++) {
            let path = paths[i];
            if (path.isPossible) {
                path.isSelected = true;
                return i;
            }
        }
        return -1;
    }

    render() {
        if (this.state.info === null) return null;
        else return React.createElement("div", {
            className: "appWrapper"
        }, React.createElement("div", {
            className: "sidebar"
        }, React.createElement(Sidebar, {
            routes: this.state.routes,
            search: this.state,
            onSearchChange: e => this.setState(e),
            onSelectIndex: i => this.setState(prevState => ({routes: this._selectRoute(prevState.routes, i)}))
        })), React.createElement("div", {
            className: "map"
        }, React.createElement(Map, {
            info: this.state.info,
            routes: this.state.routes,
            from: this.state.from,
            to: this.state.to,
            onSubmit: e => this.setState(e)
        })));
    }

    _selectRoute(oldState, newSelectedRouteIndex) {
        if (oldState.selectedRouteIndex >= 0) oldState.paths[oldState.selectedRouteIndex].isSelected = false;
        oldState.paths[newSelectedRouteIndex].isSelected = true;
        oldState.selectedRouteIndex = newSelectedRouteIndex;
        return oldState;
    }

}