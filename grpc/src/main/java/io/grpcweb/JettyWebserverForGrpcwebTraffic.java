/*
 * Copyright 2020  Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.grpcweb;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.logging.Logger;

import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.Resource;

public class JettyWebserverForGrpcwebTraffic {
  private static final Logger LOG =
      Logger.getLogger(MethodHandles.lookup().lookupClass().getName());

  private final int mGrpcwebPort;

  public JettyWebserverForGrpcwebTraffic(int grpcwebPort) {
    mGrpcwebPort = grpcwebPort;
  }

  public org.eclipse.jetty.server.Server start() throws URISyntaxException, IOException {
    // Start a jetty server to listen on the grpc-web port#
    org.eclipse.jetty.server.Server jServer = new org.eclipse.jetty.server.Server(mGrpcwebPort);

    ServletContextHandler context = new ServletContextHandler();
    context.setBaseResource(Resource.newClassPathResource("/META-INF/resources"));
//    context.addFilter(CorsFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));
//    context.addServlet(GrpcWebTrafficServlet.class, "/api/*");

    ServletHolder defaultServlet = new ServletHolder("default", DefaultServlet.class);
    defaultServlet.setInitParameter("dirAllowed","true");

    context.addServlet(defaultServlet,"/");

    jServer.setHandler(context);

    try {
      jServer.start();
    } catch (Exception e) {
      LOG.warning("Jetty Server couldn't be started. " + e.getLocalizedMessage());
      e.printStackTrace();
      return null;
    }
    LOG.info("****  started gRPC-web Service on port# " + mGrpcwebPort);
    jServer.setStopAtShutdown(true);
    return jServer;
  }
}
