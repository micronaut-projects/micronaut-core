/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.tracing.brave.instrument.http;

import brave.Span;
import brave.http.HttpServerHandler;
import brave.http.HttpServerRequest;
import brave.http.HttpServerResponse;
import brave.http.HttpTracing;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.*;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.tracing.instrument.http.AbstractOpenTracingFilter;
import org.reactivestreams.Publisher;

/**
 * Instruments incoming HTTP requests.
 *
 * @author graemerocher
 * @since 1.0
 */
@Filter(AbstractOpenTracingFilter.SERVER_PATH)
@Requires(beans = HttpServerHandler.class)
public class BraveTracingServerFilter extends AbstractBraveTracingFilter implements HttpServerFilter {

    private final HttpServerHandler<HttpServerRequest, HttpServerResponse> serverHandler;
    private final io.opentracing.Tracer openTracer;

    /**
     * @param httpTracing The {@link HttpTracing} instance
     * @param openTracer The open tracing instance
     * @param serverHandler The {@link HttpServerHandler} instance
     */
    public BraveTracingServerFilter(
            HttpTracing httpTracing,
            io.opentracing.Tracer openTracer,
            HttpServerHandler<HttpServerRequest, HttpServerResponse> serverHandler) {
        super(httpTracing);
        this.openTracer = openTracer;
        this.serverHandler = serverHandler;
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        HttpServerRequest httpServerRequest = mapRequest(request);
        Span span = serverHandler.handleReceive(httpServerRequest);
        return new HttpServerTracingPublisher(
                chain.proceed(request),
                request,
                serverHandler,
                httpTracing,
                openTracer,
                span
        );
    }

    private HttpServerRequest mapRequest(HttpRequest<?> request) {
        return new HttpServerRequest() {
                @Override
                public String method() {
                    return request.getMethodName();
                }

                @Override
                public String path() {
                    return request.getPath();
                }

                @Override
                public String url() {
                    return request.getUri().toString();
                }

                @Override
                public String header(String name) {
                    return request.getHeaders().get(name);
                }

                @Override
                public Object unwrap() {
                    return request;
                }

            };
    }

}
