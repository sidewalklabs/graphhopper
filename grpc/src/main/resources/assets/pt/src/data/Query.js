import Point from "./Point.js";

const TimeOption = {
    ARRIVAL: 1,
    DEPARTURE: 2
};

const CreateQuery = (baseUrl, search) => {
    let url = new URL(baseUrl);
    url.searchParams.delete("point");
    url.searchParams.append("point", [search.from.lat, search.from.long]);
    url.searchParams.append("point", [search.to.lat, search.to.long]);
    let time = search.departureDateTime
            .clone()     //otherwise the UI also displays utc time.
            .utc()
            .format();
    url.searchParams.set("pt.earliest_departure_time", time);
    if (search.timeOption === TimeOption.ARRIVAL) {
        url.searchParams.set("pt.arrive_by", true);
    } else {
        url.searchParams.set("pt.arrive_by", false);
    }
    url.searchParams.set("locale", "en-US");
    url.searchParams.set("profile", "pt");
    url.searchParams.set("pt.limit_solutions", search.limitSolutions);
    url.searchParams.set("pt.max_profile_duration", search.maxProfileDuration);
    url.searchParams.set("pt.beta_walk_time", search.betaWalkTime);
    url.searchParams.set("pt.limit_street_time", search.limitStreetTimeSeconds);
    url.searchParams.set("pt.use_pareto", search.usePareto);
    url.searchParams.set("pt.beta_transfers", search.betaTransfers);
    return url.toString();
};

const ParseQuery = (search, searchParams) => {
    function parsePoints(searchParams) {
        const points = searchParams.getAll("point");
        if (points.length == 2) {
            search.from = Point.createFromString(points[0]);
            search.to = Point.createFromString(points[1]);
        }
    }

    function parseDepartureTime(searchParams) {
        const departureDateTime = searchParams.get("pt.earliest_departure_time");
        if (departureDateTime) {
            search.departureDateTime = moment(departureDateTime);

            const arriveBy = searchParams.get("pt.arrive_by");
            if (arriveBy && arriveBy == "true") {
                search.timeOption = TimeOption.ARRIVAL;
            } else {
                search.timeOption = TimeOption.DEPARTURE;
            }
        }
    }

    function parse(urlKey, searchKey, searchParams) {
        const value = searchParams.get(urlKey);
        if (value) {
            search[searchKey] = value;
        }
    }

    parsePoints(searchParams);
    parseDepartureTime(searchParams);
    parse("pt.profile", "rangeQuery", searchParams);
    parse("pt.profile_duration", "rangeQueryDuration", searchParams);
    parse("pt.ignore_transfers", "ignoreTransfers", searchParams);
    parse("pt.max_profile_duration", "maxProfileDuration", searchParams);
    parse("pt.beta_walk_time", "betaWalkTime", searchParams);
    parse("pt.limit_street_time", "limitStreetTimeSeconds", searchParams)
    parse("pt.use_pareto", "usePareto", searchParams);
    parse("pt.beta_transfers", "betaTransfers", searchParams);
    return search;
};

export {CreateQuery, ParseQuery, TimeOption};
