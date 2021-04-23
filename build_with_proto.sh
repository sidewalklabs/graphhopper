#!/bin/sh
mkdir -p ./grpc/src/main/proto
cp ./idls/model/router.proto ./grpc/src/main/proto/router.proto
mkdir -p ./grpc/src/main/resources/assets/pt/src/grpc

# Build both the vanilla JS bindings for our proto, and the gRPC-web bindings for the browser
# (The two plugins interpret relative paths differently, so the gRPC-web bindings end up at a
# weird location in the source tree. I just accepted this and moved on and adapted the client code rather than trying
# to force something else. Feel free to improve..)
protoc grpc/src/main/proto/router.proto --js_out=import_style=commonjs:grpc/src/main/resources/assets/pt/src/grpc --grpc-web_out=import_style=commonjs,mode=grpcwebtext:grpc/src/main/resources/assets/pt/src/grpc

# Install both JS dependencies and the webpack build tool
npm install
npm run build -- --config grpc/src/main/resources/assets/pt/webpack.config.js

mvn -s maven_settings.xml --projects grpc -am -DskipTests=true package
mvn -s maven_settings.xml --projects web -am -DskipTests=true package
