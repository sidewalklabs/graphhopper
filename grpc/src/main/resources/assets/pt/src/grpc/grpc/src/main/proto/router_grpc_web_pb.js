/**
 * @fileoverview gRPC-Web generated client stub for router
 * @enhanceable
 * @public
 */

// GENERATED CODE -- DO NOT EDIT!


/* eslint-disable */
// @ts-nocheck



const grpc = {};
grpc.web = require('grpc-web');


var google_protobuf_timestamp_pb = require('google-protobuf/google/protobuf/timestamp_pb.js')
const proto = {};
proto.router = require('./router_pb.js');

/**
 * @param {string} hostname
 * @param {?Object} credentials
 * @param {?Object} options
 * @constructor
 * @struct
 * @final
 */
proto.router.RouterClient =
    function(hostname, credentials, options) {
  if (!options) options = {};
  options['format'] = 'text';

  /**
   * @private @const {!grpc.web.GrpcWebClientBase} The client
   */
  this.client_ = new grpc.web.GrpcWebClientBase(options);

  /**
   * @private @const {string} The hostname
   */
  this.hostname_ = hostname;

};


/**
 * @param {string} hostname
 * @param {?Object} credentials
 * @param {?Object} options
 * @constructor
 * @struct
 * @final
 */
proto.router.RouterPromiseClient =
    function(hostname, credentials, options) {
  if (!options) options = {};
  options['format'] = 'text';

  /**
   * @private @const {!grpc.web.GrpcWebClientBase} The client
   */
  this.client_ = new grpc.web.GrpcWebClientBase(options);

  /**
   * @private @const {string} The hostname
   */
  this.hostname_ = hostname;

};


/**
 * @const
 * @type {!grpc.web.MethodDescriptor<
 *   !proto.router.StreetRouteRequest,
 *   !proto.router.StreetRouteReply>}
 */
const methodDescriptor_Router_RouteStreetMode = new grpc.web.MethodDescriptor(
  '/router.Router/RouteStreetMode',
  grpc.web.MethodType.UNARY,
  proto.router.StreetRouteRequest,
  proto.router.StreetRouteReply,
  /**
   * @param {!proto.router.StreetRouteRequest} request
   * @return {!Uint8Array}
   */
  function(request) {
    return request.serializeBinary();
  },
  proto.router.StreetRouteReply.deserializeBinary
);


/**
 * @const
 * @type {!grpc.web.AbstractClientBase.MethodInfo<
 *   !proto.router.StreetRouteRequest,
 *   !proto.router.StreetRouteReply>}
 */
const methodInfo_Router_RouteStreetMode = new grpc.web.AbstractClientBase.MethodInfo(
  proto.router.StreetRouteReply,
  /**
   * @param {!proto.router.StreetRouteRequest} request
   * @return {!Uint8Array}
   */
  function(request) {
    return request.serializeBinary();
  },
  proto.router.StreetRouteReply.deserializeBinary
);


/**
 * @param {!proto.router.StreetRouteRequest} request The
 *     request proto
 * @param {?Object<string, string>} metadata User defined
 *     call metadata
 * @param {function(?grpc.web.Error, ?proto.router.StreetRouteReply)}
 *     callback The callback function(error, response)
 * @return {!grpc.web.ClientReadableStream<!proto.router.StreetRouteReply>|undefined}
 *     The XHR Node Readable Stream
 */
proto.router.RouterClient.prototype.routeStreetMode =
    function(request, metadata, callback) {
  return this.client_.rpcCall(this.hostname_ +
      '/router.Router/RouteStreetMode',
      request,
      metadata || {},
      methodDescriptor_Router_RouteStreetMode,
      callback);
};


/**
 * @param {!proto.router.StreetRouteRequest} request The
 *     request proto
 * @param {?Object<string, string>} metadata User defined
 *     call metadata
 * @return {!Promise<!proto.router.StreetRouteReply>}
 *     Promise that resolves to the response
 */
proto.router.RouterPromiseClient.prototype.routeStreetMode =
    function(request, metadata) {
  return this.client_.unaryCall(this.hostname_ +
      '/router.Router/RouteStreetMode',
      request,
      metadata || {},
      methodDescriptor_Router_RouteStreetMode);
};


/**
 * @const
 * @type {!grpc.web.MethodDescriptor<
 *   !proto.router.PtRouteRequest,
 *   !proto.router.PtRouteReply>}
 */
const methodDescriptor_Router_RoutePt = new grpc.web.MethodDescriptor(
  '/router.Router/RoutePt',
  grpc.web.MethodType.UNARY,
  proto.router.PtRouteRequest,
  proto.router.PtRouteReply,
  /**
   * @param {!proto.router.PtRouteRequest} request
   * @return {!Uint8Array}
   */
  function(request) {
    return request.serializeBinary();
  },
  proto.router.PtRouteReply.deserializeBinary
);


/**
 * @const
 * @type {!grpc.web.AbstractClientBase.MethodInfo<
 *   !proto.router.PtRouteRequest,
 *   !proto.router.PtRouteReply>}
 */
const methodInfo_Router_RoutePt = new grpc.web.AbstractClientBase.MethodInfo(
  proto.router.PtRouteReply,
  /**
   * @param {!proto.router.PtRouteRequest} request
   * @return {!Uint8Array}
   */
  function(request) {
    return request.serializeBinary();
  },
  proto.router.PtRouteReply.deserializeBinary
);


/**
 * @param {!proto.router.PtRouteRequest} request The
 *     request proto
 * @param {?Object<string, string>} metadata User defined
 *     call metadata
 * @param {function(?grpc.web.Error, ?proto.router.PtRouteReply)}
 *     callback The callback function(error, response)
 * @return {!grpc.web.ClientReadableStream<!proto.router.PtRouteReply>|undefined}
 *     The XHR Node Readable Stream
 */
proto.router.RouterClient.prototype.routePt =
    function(request, metadata, callback) {
  return this.client_.rpcCall(this.hostname_ +
      '/router.Router/RoutePt',
      request,
      metadata || {},
      methodDescriptor_Router_RoutePt,
      callback);
};


/**
 * @param {!proto.router.PtRouteRequest} request The
 *     request proto
 * @param {?Object<string, string>} metadata User defined
 *     call metadata
 * @return {!Promise<!proto.router.PtRouteReply>}
 *     Promise that resolves to the response
 */
proto.router.RouterPromiseClient.prototype.routePt =
    function(request, metadata) {
  return this.client_.unaryCall(this.hostname_ +
      '/router.Router/RoutePt',
      request,
      metadata || {},
      methodDescriptor_Router_RoutePt);
};


/**
 * @const
 * @type {!grpc.web.MethodDescriptor<
 *   !proto.router.MatrixRouteRequest,
 *   !proto.router.MatrixRouteReply>}
 */
const methodDescriptor_Router_RouteMatrix = new grpc.web.MethodDescriptor(
  '/router.Router/RouteMatrix',
  grpc.web.MethodType.UNARY,
  proto.router.MatrixRouteRequest,
  proto.router.MatrixRouteReply,
  /**
   * @param {!proto.router.MatrixRouteRequest} request
   * @return {!Uint8Array}
   */
  function(request) {
    return request.serializeBinary();
  },
  proto.router.MatrixRouteReply.deserializeBinary
);


/**
 * @const
 * @type {!grpc.web.AbstractClientBase.MethodInfo<
 *   !proto.router.MatrixRouteRequest,
 *   !proto.router.MatrixRouteReply>}
 */
const methodInfo_Router_RouteMatrix = new grpc.web.AbstractClientBase.MethodInfo(
  proto.router.MatrixRouteReply,
  /**
   * @param {!proto.router.MatrixRouteRequest} request
   * @return {!Uint8Array}
   */
  function(request) {
    return request.serializeBinary();
  },
  proto.router.MatrixRouteReply.deserializeBinary
);


/**
 * @param {!proto.router.MatrixRouteRequest} request The
 *     request proto
 * @param {?Object<string, string>} metadata User defined
 *     call metadata
 * @param {function(?grpc.web.Error, ?proto.router.MatrixRouteReply)}
 *     callback The callback function(error, response)
 * @return {!grpc.web.ClientReadableStream<!proto.router.MatrixRouteReply>|undefined}
 *     The XHR Node Readable Stream
 */
proto.router.RouterClient.prototype.routeMatrix =
    function(request, metadata, callback) {
  return this.client_.rpcCall(this.hostname_ +
      '/router.Router/RouteMatrix',
      request,
      metadata || {},
      methodDescriptor_Router_RouteMatrix,
      callback);
};


/**
 * @param {!proto.router.MatrixRouteRequest} request The
 *     request proto
 * @param {?Object<string, string>} metadata User defined
 *     call metadata
 * @return {!Promise<!proto.router.MatrixRouteReply>}
 *     Promise that resolves to the response
 */
proto.router.RouterPromiseClient.prototype.routeMatrix =
    function(request, metadata) {
  return this.client_.unaryCall(this.hostname_ +
      '/router.Router/RouteMatrix',
      request,
      metadata || {},
      methodDescriptor_Router_RouteMatrix);
};


/**
 * @const
 * @type {!grpc.web.MethodDescriptor<
 *   !proto.router.InfoRequest,
 *   !proto.router.InfoReply>}
 */
const methodDescriptor_Router_Info = new grpc.web.MethodDescriptor(
  '/router.Router/Info',
  grpc.web.MethodType.UNARY,
  proto.router.InfoRequest,
  proto.router.InfoReply,
  /**
   * @param {!proto.router.InfoRequest} request
   * @return {!Uint8Array}
   */
  function(request) {
    return request.serializeBinary();
  },
  proto.router.InfoReply.deserializeBinary
);


/**
 * @const
 * @type {!grpc.web.AbstractClientBase.MethodInfo<
 *   !proto.router.InfoRequest,
 *   !proto.router.InfoReply>}
 */
const methodInfo_Router_Info = new grpc.web.AbstractClientBase.MethodInfo(
  proto.router.InfoReply,
  /**
   * @param {!proto.router.InfoRequest} request
   * @return {!Uint8Array}
   */
  function(request) {
    return request.serializeBinary();
  },
  proto.router.InfoReply.deserializeBinary
);


/**
 * @param {!proto.router.InfoRequest} request The
 *     request proto
 * @param {?Object<string, string>} metadata User defined
 *     call metadata
 * @param {function(?grpc.web.Error, ?proto.router.InfoReply)}
 *     callback The callback function(error, response)
 * @return {!grpc.web.ClientReadableStream<!proto.router.InfoReply>|undefined}
 *     The XHR Node Readable Stream
 */
proto.router.RouterClient.prototype.info =
    function(request, metadata, callback) {
  return this.client_.rpcCall(this.hostname_ +
      '/router.Router/Info',
      request,
      metadata || {},
      methodDescriptor_Router_Info,
      callback);
};


/**
 * @param {!proto.router.InfoRequest} request The
 *     request proto
 * @param {?Object<string, string>} metadata User defined
 *     call metadata
 * @return {!Promise<!proto.router.InfoReply>}
 *     Promise that resolves to the response
 */
proto.router.RouterPromiseClient.prototype.info =
    function(request, metadata) {
  return this.client_.unaryCall(this.hostname_ +
      '/router.Router/Info',
      request,
      metadata || {},
      methodDescriptor_Router_Info);
};


module.exports = proto.router;

