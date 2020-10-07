#!/bin/sh
cp ./idls/model/router.proto ./grpc/src/main/proto/router.proto
mvn -s maven_settings.xml --projects grpc -am -DskipTests=true package
