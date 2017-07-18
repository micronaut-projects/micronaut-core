/*
 * Copyright 2017 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.particleframework.http.server.greenlightning;

import com.ociweb.gl.api.*;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import org.particleframework.context.ApplicationContext;
import org.particleframework.http.HttpMethod;
import org.particleframework.web.router.RouteMatch;
import org.particleframework.web.router.Router;

import java.util.Optional;


public class ParticleGreenLightningApp implements GreenApp {
    protected int ROUTE_ID;
    protected final ApplicationContext applicationContext;
    protected final int port;
    protected final String host;
    protected GreenRuntime runtime;

    public ParticleGreenLightningApp(ApplicationContext applicationContext, String host, int port) {
        this.applicationContext = applicationContext;
        this.port = port;
        this.host = host;
    }

    @Override
    public void declareConfiguration(final Builder builder) {
        builder.enableServer(false, false, host, port);
        ROUTE_ID = builder.registerRoute("/${path}");
    }

    public void declareBehavior(final GreenRuntime runtime) {
        final RestListener adder = new TemporarySpikeServer(runtime, applicationContext);
        runtime.addRestListener(adder).includeRoutes(ROUTE_ID);
        this.runtime = runtime;
    }

    public void stop() {
        runtime.shutdownRuntime();
    }
}

// There is nothing best-practice about this... just a spike as a starting point.
class TemporarySpikeServer implements RestListener {

    protected final GreenCommandChannel greenCommandChannel;
    protected final ApplicationContext applicationContext;

    public TemporarySpikeServer(final GreenRuntime runtime, final ApplicationContext applicationContext) {
        greenCommandChannel = runtime.newCommandChannel(NET_RESPONDER);
        this.applicationContext = applicationContext;
    }

    @Override
    public boolean restRequest(final HTTPRequestReader request) {
        final Appendable routePath = new StringBuilder("/");
        request.getText("path".getBytes(), routePath);

        final Optional<Router> routerBean = applicationContext.findBean(Router.class);

        final Optional<RouteMatch> routeMatch = routerBean.flatMap((router) -> {
                    return router.find(HttpMethod.GET, routePath.toString())
//                            .filter((match) -> match.test( ?? ))
                            .findFirst();
                }
        );

        routeMatch.ifPresent((RouteMatch route) -> {
            final Object result = route.execute();
            final NetWritable responseWritable = writer -> writer.writeUTF8Text(result.toString());
            greenCommandChannel.publishHTTPResponse(request, 200,
                    request.getRequestContext() | HTTPFieldReader.END_OF_RESPONSE,
                    HTTPContentTypeDefaults.TXT,
                    responseWritable);
        });

        return true;
    }
}
