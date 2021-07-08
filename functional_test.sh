#!/bin/bash

set +ex

if [ "$#" -lt 1 ]; then
    echo "Usage: functional_test.sh TAG"
    exit 1
fi

TAG=$1

TMPDIR=$(mktemp -d)

# To run this script locally, you may need to run `docker pull $tag` from the model repo,
# where your credentials will be populated.
DOCKER_IMAGE_TAG="us.gcr.io/model-159019/gh:$TAG"

# Import data into graphhopper's internal format
docker run \
    -v "$(pwd)/web/test-data/:/graphhopper/test-data/" \
    -v "$TMPDIR:/graphhopper/transit_data/"\
    --rm \
     "$DOCKER_IMAGE_TAG" \
     java -Xmx2g -Xms1g -XX:+UseG1GC -XX:MetaspaceSize=100M \
     -Ddw.graphhopper.datareader.file=test-data/kansas-city-extract-mini.osm.pbf \
     -Ddw.graphhopper.gtfs.file=test-data/mini_kc_gtfs.tar \
     -classpath web/target/graphhopper-web-1.0-SNAPSHOT.jar -server com.graphhopper.http.GraphHopperApplication import default_gh_config.yaml

# Run link-mapping step
docker run \
    -v "$TMPDIR:/graphhopper/transit_data/"\
    --rm \
    "$DOCKER_IMAGE_TAG" \
    java -Xmx2g -Xms1g -XX:+UseG1GC -XX:MetaspaceSize=100M \
    -classpath web/target/graphhopper-web-1.0-SNAPSHOT.jar com.graphhopper.http.GraphHopperApplication gtfs_links default_gh_config.yaml

# Run server in background
docker run --rm  --name functional_test_server -p 50051:50051 -p 8998:8998 -v "$TMPDIR:/graphhopper/transit_data/" \
    "$DOCKER_IMAGE_TAG" &

echo "Waiting for graphhopper server to start up"
sleep 30

# Make a request for a couple of points in the minikc region
grpcurl -d @ -plaintext localhost:50051 router.Router/RouteStreetMode > "$TMPDIR"/street_response.json <<EOM
{
"points":[{"lat":38.96637569955874,"lon":-94.70833304570988},{"lat":38.959204519370815,"lon":-94.69174071738964}],
"profile": "car"
}
EOM

if ! [ -s "$TMPDIR"/street_response.json ] || ! jq -e .paths < "$TMPDIR"/street_response.json; then
    echo "Street response empty or not valid json:"
    cat "$TMPDIR"/street_response.json
    exit 1
fi

# Make a PT request too
grpcurl -d @ -plaintext localhost:50051 router.Router/RoutePt  > "$TMPDIR"/pt_response.json <<EOM
{
"points":[{"lat":38.96637569955874,"lon":-94.70833304570988},{"lat":38.959204519370815,"lon":-94.69174071738964}],
"earliest_departure_time":"2018-02-04T08:25:00Z",
"limit_solutions":4,
"max_profile_duration":10,
"beta_walk_time":1.5,
"limit_street_time_seconds":1440,
"use_pareto":false,
"betaTransfers":1440000
}
EOM

if ! [ -s "$TMPDIR"/pt_response.json ] || ! jq -e .paths < "$TMPDIR"/pt_response.json; then
    echo "PT response empty or not valid json:"
    cat "$TMPDIR"/pt_response.json
    exit 1
fi

docker kill functional_test_server
