#!/bin/sh
mkdir -p ./grpc/src/main/proto
cp ./idls/model/router.proto ./grpc/src/main/proto/router.proto
mvn -s maven_settings.xml --projects grpc web -am -DskipTests=true package
