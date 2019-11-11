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

import brave.Tracer;
import brave.http.HttpClientHandler;
import brave.http.HttpClientRequest;
import brave.http.HttpClientResponse;
import brave.http.HttpTracing;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.tracing.instrument.http.TraceRequestAttributes;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * A Publisher that handles HTTP client request tracing.
 *
 * @author graemerocher
 * @since 1.0
 */
@SuppressWarnings("PublisherImplementation")
class HttpClientTracingPublisher implements Publisher<HttpResponse<?>> {

    private final Publisher<HttpResponse<?>> publisher;
    private final HttpClientHandler<HttpClientRequest, HttpClientResponse> clientHandler;
    private final MutableHttpRequest<?> request;
    private final Tracer tracer;

    /**
     * Construct a publisher to handle HTTP client request tracing.
     *
     * @param publisher     The response publisher
     * @param request       An extended version of request that allows mutating
     * @param clientHandler The standardize way to instrument client
     * @param httpTracing   HttpTracing
     */
    HttpClientTracingPublisher(
            Publisher<HttpResponse<?>> publisher,
            MutableHttpRequest<?> request,
            HttpClientHandler<HttpClientRequest, HttpClientResponse> clientHandler,
            HttpTracing httpTracing) {
        this.publisher = publisher;
        this.request = request;
        this.clientHandler = clientHandler;
        this.tracer = httpTracing.tracing().tracer();
    }

    @Override
    public void subscribe(Subscriber<? super HttpResponse<?>> actual) {
        HttpClientRequest httpClientRequest = mapRequest(request);
        brave.Span span = clientHandler.handleSend(httpClientRequest);
        request.getAttribute(HttpAttributes.SERVICE_ID, String.class)
                .filter(StringUtils::isNotEmpty)
                .ifPresent(span::remoteServiceName);
        request.setAttribute(TraceRequestAttributes.CURRENT_SPAN, span);
        try (Tracer.SpanInScope ignored = tracer.withSpanInScope(span)) {
            publisher.subscribe(new Subscriber<HttpResponse<?>>() {
                @Override
                public void onSubscribe(Subscription s) {
                    try (Tracer.SpanInScope ignored = tracer.withSpanInScope(span)) {
                        actual.onSubscribe(s);
                    }
                }

                @Override
                public void onNext(HttpResponse<?> response) {
                    try (Tracer.SpanInScope ignored = tracer.withSpanInScope(span)) {
                        clientHandler.handleReceive(mapResponse(request, response), null, span);
                        actual.onNext(response);
                    }
                }

                @Override
                public void onError(Throwable error) {
                    try (Tracer.SpanInScope ignored = tracer.withSpanInScope(span)) {
                        if (error instanceof HttpClientResponseException) {
                            HttpClientResponseException e = (HttpClientResponseException) error;
                            clientHandler.handleReceive(mapResponse(request, e.getResponse()), e, span);
                        } else {
                            span.error(error);
                            span.finish();
                        }

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

    private HttpClientRequest mapRequest(MutableHttpRequest<?> request) {
        return new HttpClientRequest() {

            @Override
            public void header(String name, String value) {
                request.header(name, value);
            }

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

    private HttpClientResponse mapResponse(HttpRequest<?> request, HttpResponse<?> response) {
        return new HttpClientResponse() {
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
}
