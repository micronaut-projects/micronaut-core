/*
 * Copyright 2017-2020 original authors
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
import brave.Tracer;
import brave.Tracing;
import brave.http.HttpServerHandler;
import brave.http.HttpServerRequest;
import brave.http.HttpServerResponse;
import brave.http.HttpTracing;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.tracing.instrument.http.TraceRequestAttributes;
import io.micronaut.tracing.instrument.util.ScopePropagationPublisher;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Optional;

/**
 * A Publisher that handles HTTP client server tracing.
 *
 * @author graemerocher
 * @since 1.0
 */
public class HttpServerTracingPublisher implements Publisher<MutableHttpResponse<?>> {

    private final Publisher<MutableHttpResponse<?>> publisher;
    private final HttpServerHandler<HttpServerRequest, HttpServerResponse> serverHandler;
    private final HttpRequest<?> request;
    private final Tracer tracer;
    private final io.opentracing.Tracer openTracer;
    private final Span initialSpan;

    /**
     * Construct a publisher to handle HTTP client request tracing.
     *
     * @param publisher     The response publisher
     * @param request       An extended version of request that allows mutating
     * @param serverHandler The standardize way to instrument client
     * @param httpTracing   HttpTracing
     * @param openTracer    The open tracing instance
     * @param initialSpan   The initial span
     */
    HttpServerTracingPublisher(
            Publisher<MutableHttpResponse<?>> publisher,
            HttpRequest<?> request,
            HttpServerHandler<HttpServerRequest, HttpServerResponse> serverHandler,
            HttpTracing httpTracing,
            io.opentracing.Tracer openTracer,
            Span initialSpan) {
        this.publisher = publisher;
        this.request = request;
        this.initialSpan = initialSpan;
        this.serverHandler = serverHandler;
        Tracing tracing = httpTracing.tracing();
        this.tracer = tracing.tracer();
        this.openTracer = openTracer;
    }

    @Override
    public void subscribe(Subscriber<? super MutableHttpResponse<?>> actual) {
        Span span = initialSpan;
        request.setAttribute(TraceRequestAttributes.CURRENT_SPAN, span);
        try (Tracer.SpanInScope ignored = tracer.withSpanInScope(span)) {
            publisher.subscribe(new Subscriber<MutableHttpResponse<?>>() {
                @Override
                public void onSubscribe(Subscription s) {
                    try (Tracer.SpanInScope ignored = tracer.withSpanInScope(span)) {
                        actual.onSubscribe(s);
                    }
                }

                @Override
                public void onNext(MutableHttpResponse<?> response) {
                    try (Tracer.SpanInScope ignored = tracer.withSpanInScope(span)) {
                        Optional<?> body = response.getBody();
                        if (body.isPresent()) {
                            Object o = body.get();
                            if (Publishers.isConvertibleToPublisher(o)) {
                                Class<?> type = o.getClass();
                                Publisher<?> resultPublisher = Publishers.convertPublisher(o, Publisher.class);
                                Publisher scopedPublisher = new ScopePropagationPublisher(
                                        resultPublisher,
                                        openTracer,
                                        openTracer.activeSpan()
                                );

                                ((MutableHttpResponse) response).body(Publishers.convertPublisher(scopedPublisher, type));
                            }
                        }
                        serverHandler.handleSend(mapResponse(request, response), null, span);
                        actual.onNext(response);
                    }
                }

                @Override
                public void onError(Throwable error) {
                    try (Tracer.SpanInScope ignored = tracer.withSpanInScope(span)) {
                        int statusCode = 500;
                        if (error instanceof HttpStatusException) {
                            statusCode = ((HttpStatusException) error).getStatus().getCode();
                        }
                        serverHandler.handleSend(mapResponse(request, statusCode), error, span);
                        actual.onError(error);
                    }
                }

                @Override
                public void onComplete() {
                    actual.onComplete();
                }
            });
        }
    }

    private HttpServerResponse mapResponse(HttpRequest<?> request, HttpResponse<?> response) {
        return new HttpServerResponse() {
            @Override
            public Object unwrap() {
                return response;
            }

            @Override
            public String method() {
                return request.getMethodName();
            }

            @Override
            public String route() {
                return request.getAttribute(HttpAttributes.URI_TEMPLATE, String.class).orElse(null);
            }

            @Override
            public int statusCode() {
                return response.getStatus().getCode();
            }
        };
    }

    private HttpServerResponse mapResponse(HttpRequest<?> request, int statusCode) {
        return new HttpServerResponse() {
            @Override
            public Object unwrap() {
                return this;
            }

            @Override
            public String method() {
                return request.getMethodName();
            }

            @Override
            public String route() {
                return request.getAttribute(HttpAttributes.URI_TEMPLATE, String.class).orElse(null);
            }

            @Override
            public int statusCode() {
                return statusCode;
            }
        };
    }
}
