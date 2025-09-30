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
import io.micronaut.core.execution.ImperativeExecutionFlow;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.netty.NettyMutableHttpResponse;
import io.micronaut.http.netty.body.NettyByteBodyFactory;
import io.micronaut.http.server.RequestLifecycle;
import io.micronaut.http.server.netty.handler.OutboundAccess;
import io.micronaut.http.server.types.files.FileCustomizableResponseType;
import io.micronaut.http.server.types.files.StreamedFile;
import io.micronaut.http.server.types.files.SystemFile;
import io.micronaut.web.router.RouteMatch;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.DefaultHttpContent;
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
    private final OutboundAccess outboundAccess;

    /**
     * Should only be used where netty-specific stuff is needed, such as reading the body or
     * writing the response.
     */
    private NettyHttpRequest<?> nettyRequest;

    NettyRequestLifecycle(RoutingInBoundHandler rib, OutboundAccess outboundAccess) {
        super(rib.routeExecutor);
        this.rib = rib;
        this.outboundAccess = outboundAccess;
    }

    void handleNormal(NettyHttpRequest<?> request) {
        this.nettyRequest = request;

        if (LOG.isDebugEnabled()) {
            HttpMethod httpMethod = request.getMethod();
            LOG.debug("Request {} {}", httpMethod, request.getUri());
        }

        ExecutionFlow<HttpResponse<?>> result;

        // handle decoding failure
        DecoderResult decoderResult = request.getNativeRequest().decoderResult();
        if (decoderResult.isFailure()) {
            Throwable cause = decoderResult.cause();
            HttpStatus status = cause instanceof TooLongFrameException ? HttpStatus.REQUEST_ENTITY_TOO_LARGE : HttpStatus.BAD_REQUEST;
            try {
                result = onStatusError(
                    request,
                    HttpResponse.status(status),
                    status.getReason()
                );
            } catch (Exception e) {
                result = ExecutionFlow.error(e);
            }
        } else {
            try {
                result = normalFlow(request);
            } catch (Exception e) {
                handleException(request, e);
                return;
            }
        }

        ImperativeExecutionFlow<HttpResponse<?>> imperativeFlow = result.tryComplete();
        if (imperativeFlow != null) {
            Object value = ((ImperativeExecutionFlow<?>) imperativeFlow).getValue();
            // usually this is a MutableHttpResponse, avoid scalability issues here
            HttpResponse<?> response = value instanceof NettyMutableHttpResponse<?> mut ? mut : (HttpResponse<?>) value;
            rib.writeResponse(outboundAccess, request, response, imperativeFlow.getError());
        } else {
            result.onComplete((response, throwable) -> rib.writeResponse(outboundAccess, request, response, throwable));
        }
    }

    @Nullable
    @Override
    protected FileCustomizableResponseType findFile(HttpRequest<?> request) {
        Optional<URL> optionalUrl = rib.staticResourceResolver.resolve(request.getUri().getPath());
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
    protected ExecutionFlow<RouteMatch<?>> fulfillArguments(RouteMatch<?> routeMatch, HttpRequest<?> request) {
        // handle decoding failure
        DecoderResult decoderResult = nettyRequest.getNativeRequest().decoderResult();
        if (decoderResult.isFailure()) {
            return ExecutionFlow.error(decoderResult.cause());
        }
        return super.fulfillArguments(routeMatch, request).flatMap(this::waitForBody);
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
                HttpContentProcessorAsReactiveProcessor.asPublisher(processor, NettyByteBodyFactory.toByteBufs(rootBody).map(DefaultHttpContent::new)).subscribe(formRouteCompleter);
                nettyRequest.addRouteWaitsFor(formRouteCompleter.getExecute());
            } catch (Throwable e) {
                return ExecutionFlow.error(e);
            }
        }
        return nettyRequest.getRouteWaitsFor().map(v -> routeMatch);
    }

    void handleException(NettyHttpRequest<?> nettyRequest, Throwable cause) {
        onError(nettyRequest, cause).onComplete((response, throwable) -> rib.writeResponse(outboundAccess, nettyRequest, response, throwable));
    }

}
