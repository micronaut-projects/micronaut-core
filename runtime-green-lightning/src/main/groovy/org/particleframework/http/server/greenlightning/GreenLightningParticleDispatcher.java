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
import org.particleframework.http.HttpMethod;
import org.particleframework.http.HttpStatus;
import org.particleframework.web.router.Router;
import org.particleframework.web.router.UriRouteMatch;

import java.util.Optional;

class GreenLightningParticleDispatcher implements RestListener {

    protected final GreenCommandChannel greenCommandChannel;
    protected final Optional<Router> router;

    public GreenLightningParticleDispatcher(final GreenRuntime runtime, final Optional<Router> router) {
        greenCommandChannel = runtime.newCommandChannel(NET_RESPONDER);
        this.router = router;
    }

    @Override
    public boolean restRequest(final HTTPRequestReader request) {
        final Appendable routePath = new StringBuilder();
        request.getRoutePath(routePath);

        final Optional<UriRouteMatch<Object>> routeMatch = router.flatMap((router) -> {
                    return router.find(HttpMethod.GET, routePath.toString())
//                            .filter((match) -> match.test( ?? ))
                            .findFirst();
                }
        );

        if(routeMatch.isPresent()) {
            UriRouteMatch<Object> route = routeMatch.get();
            final Object result = route.execute();
            final Writable responseWritable = writer -> writer.writeUTF8Text(result.toString());
            greenCommandChannel.publishHTTPResponse(request, HttpStatus.OK.getCode(),
                    HTTPContentTypeDefaults.TXT,
                    responseWritable);
        } else {
            greenCommandChannel.publishHTTPResponse(request, HttpStatus.NOT_FOUND.getCode(), HTTPContentTypeDefaults.UNKNOWN, Writable.NO_OP);
        }

        return true;
    }
}
