/*
 * Copyright 2017-2022 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.server.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.server.RequestLifecycle;
import io.micronaut.http.server.netty.body.ByteBody;
import io.micronaut.http.server.netty.handler.PipeliningServerHandler;
import io.micronaut.http.server.types.files.FileCustomizableResponseType;
import io.micronaut.http.server.types.files.StreamedFile;
import io.micronaut.http.server.types.files.SystemFile;
import io.micronaut.web.router.RouteMatch;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.TooLongFrameException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Optional;

@Internal
final class NettyRequestLifecycle extends RequestLifecycle {
    private static final Logger LOG = LoggerFactory.getLogger(NettyRequestLifecycle.class);

    private final RoutingInBoundHandler rib;
    private final PipeliningServerHandler.OutboundAccess outboundAccess;

    /**
     * Should only be used where netty-specific stuff is needed, such as reading the body or
     * writing the response. Otherwise, use {@link #request()} which can be updated by filters
     */
    private final NettyHttpRequest<?> nettyRequest;

    NettyRequestLifecycle(RoutingInBoundHandler rib, PipeliningServerHandler.OutboundAccess outboundAccess, NettyHttpRequest<?> request) {
        super(rib.routeExecutor, request);
        this.rib = rib;
        this.outboundAccess = outboundAccess;
        this.nettyRequest = request;

        multipartEnabled(rib.multipartEnabled);
    }

    void handleNormal() {
        if (LOG.isDebugEnabled()) {
            HttpMethod httpMethod = request().getMethod();
            LOG.debug("Request {} {}", httpMethod, request().getUri());
        }

        ExecutionFlow<MutableHttpResponse<?>> result;

        try {
            // handle decoding failure
            DecoderResult decoderResult = nettyRequest.getNativeRequest().decoderResult();
            if (decoderResult.isFailure()) {
                Throwable cause = decoderResult.cause();
                HttpStatus status = cause instanceof TooLongFrameException ? HttpStatus.REQUEST_ENTITY_TOO_LARGE : HttpStatus.BAD_REQUEST;
                result = onStatusError(
                    HttpResponse.status(status),
                    status.getReason()
                );
            } else {
                result = normalFlow();
            }
            result.onComplete((response, throwable) -> rib.writeResponse(outboundAccess, nettyRequest, response, throwable));
        } catch (Exception e) {
            handleException(e);
        }
    }

    @Nullable
    @Override
    protected FileCustomizableResponseType findFile() {
        Optional<URL> optionalUrl = rib.staticResourceResolver.resolve(request().getUri().getPath());
        if (optionalUrl.isPresent()) {
            try {
                URL url = optionalUrl.get();
                if (url.getProtocol().equals("file")) {
                    File file = Paths.get(url.toURI()).toFile();
                    if (file.exists() && !file.isDirectory() && file.canRead()) {
                        return new SystemFile(file);
                    }
                }
                return new StreamedFile(url);
            } catch (URISyntaxException e) {
                //no-op
            }
        }
        return null;
    }

    @Override
    protected ExecutionFlow<RouteMatch<?>> fulfillArguments(RouteMatch<?> routeMatch) {
        // handle decoding failure
        DecoderResult decoderResult = nettyRequest.getNativeRequest().decoderResult();
        if (decoderResult.isFailure()) {
            return ExecutionFlow.error(decoderResult.cause());
        }
        return super.fulfillArguments(routeMatch).flatMap(this::waitForBody);
    }

    /**
     * If necessary (e.g. when there's a {@link Body} parameter), wait for the body to come in.
     * This method also sometimes fulfills more controller parameters with form data.
     */
    private ExecutionFlow<RouteMatch<?>> waitForBody(RouteMatch<?> routeMatch) {
        // if there is a binder that needs form content, actually process the body now. We need to
        // do this after all binders are done because all createClaimant calls must be done before
        // the FormRouteCompleter can process data.
        if (nettyRequest.hasFormRouteCompleter()) {
            FormDataHttpContentProcessor processor = new FormDataHttpContentProcessor(nettyRequest, rib.serverConfiguration);
            ByteBody rootBody = nettyRequest.byteBody();
            FormRouteCompleter formRouteCompleter = nettyRequest.formRouteCompleter();
            try {
                rootBody.processMulti(processor).handleForm(formRouteCompleter);
                nettyRequest.addRouteWaitsFor(formRouteCompleter.getExecute());
            } catch (Throwable e) {
                return ExecutionFlow.error(e);
            }
        }
        return nettyRequest.getRouteWaitsFor().map(v -> routeMatch);
    }

    void handleException(Throwable cause) {
        onError(cause).onComplete((response, throwable) -> rib.writeResponse(outboundAccess, nettyRequest, response, throwable));
    }

}
