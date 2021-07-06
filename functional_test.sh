#!/bin/bash

TMPDIR=$(mktemp -d)

# Import cmd
docker run \
    -v "$(pwd)/web/test-data/:/graphhopper/test-data/" \
    -v "$TMPDIR:/graphhopper/transit_data/"\
    --rm \
     us.gcr.io/model-159019/gh:ec387a0e \
     java -Xmx2g -Xms1g -XX:+UseG1GC -XX:MetaspaceSize=100M \
     -Ddw.graphhopper.datareader.file=test-data/kansas-city-extract-mini.osm.pbf \
     -Ddw.graphhopper.gtfs.file=test-data/mini_kc_gtfs.tar \
     -classpath web/target/graphhopper-web-1.0-SNAPSHOT.jar -server com.graphhopper.http.GraphHopperApplication import default_gh_config.yaml

# Run server in background
docker run --rm  --name functional_test_server -p 50051:50051 -p 8998:8998 -v "$TMPDIR:/graphhopper/transit_data/" \
    us.gcr.io/model-159019/gh:ec387a0e &

echo "Waiting for graphhopper server to start up"
sleep 30

# Make a request for a couple of points in the minikc region
grpcurl -d @ -plaintext localhost:50051 router.Router/RouteStreetMode <<EOM
{
"points":[{"lat":38.96637569955874,"lon":-94.70833304570988},{"lat":38.959204519370815,"lon":-94.69174071738964}],
"profile": "car"
}
EOM

docker kill functional_test_server
