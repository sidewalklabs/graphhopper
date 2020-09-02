//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.graphhopper.resources;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.GHResponse;
import com.graphhopper.gtfs.GHLocation;
import com.graphhopper.gtfs.PtRouter;
import com.graphhopper.gtfs.Request;
import com.graphhopper.http.DurationParam;
import com.graphhopper.http.GHLocationParam;
import com.graphhopper.http.WebHelper;
import com.graphhopper.util.Helper;
import com.graphhopper.util.StopWatch;
import io.dropwizard.jersey.params.AbstractParam;
import io.dropwizard.jersey.params.InstantParam;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import static com.graphhopper.util.Parameters.Details.PATH_DETAILS;

@Path("route-pt")
public class PtRouteResource {
    private final PtRouter ptRouter;

    @Inject
    public PtRouteResource(PtRouter ptRouter) {
        this.ptRouter = ptRouter;
    }

    @GET
    @Produces({"application/json"})
    public ObjectNode route(@QueryParam("point") @Size(min = 2,max = 2) List<GHLocationParam> requestPoints, @QueryParam("pt.earliest_departure_time") @NotNull InstantParam departureTimeParam, @QueryParam("pt.profile_duration") DurationParam profileDuration, @QueryParam("pt.arrive_by") @DefaultValue("false") boolean arriveBy, @QueryParam("locale") String localeStr, @QueryParam("pt.ignore_transfers") Boolean ignoreTransfers, @QueryParam("pt.profile") Boolean profileQuery, @QueryParam("pt.limit_solutions") Integer limitSolutions, @QueryParam("pt.limit_street_time") DurationParam limitStreetTime, @QueryParam("details") List<String> pathDetails) {
        StopWatch stopWatch = (new StopWatch()).start();
        List<GHLocation> points = (List)requestPoints.stream().map(AbstractParam::get).collect(Collectors.toList());
        Instant departureTime = (Instant)departureTimeParam.get();
        Request request = new Request(points, departureTime);
        request.setArriveBy(arriveBy);
        Optional.ofNullable(profileQuery).ifPresent(request::setProfileQuery);
        Optional.ofNullable(profileDuration.get()).ifPresent(request::setMaxProfileDuration);
        Optional.ofNullable(ignoreTransfers).ifPresent(request::setIgnoreTransfers);
        Optional.ofNullable(localeStr).ifPresent((s) -> {
            request.setLocale(Helper.getLocale(s));
        });
        Optional.ofNullable(limitSolutions).ifPresent(request::setLimitSolutions);
        Optional.ofNullable(limitStreetTime.get()).ifPresent(request::setLimitStreetTime);
        Optional.ofNullable(pathDetails).ifPresent(request::setPathDetails);
        GHResponse route = this.ptRouter.route(request);
        return WebHelper.jsonObject(route, true, true, false, false, (float)stopWatch.stop().getMillis());
    }
}
