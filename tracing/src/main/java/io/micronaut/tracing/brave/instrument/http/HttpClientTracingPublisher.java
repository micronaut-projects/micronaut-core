/*
 * Copyright 2017-2018 original authors
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
import brave.http.HttpClientHandler;
import brave.http.HttpTracing;
import brave.propagation.TraceContext;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.*;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.tracing.instrument.http.AbstractOpenTracingFilter;
import io.micronaut.tracing.instrument.http.TraceRequestAttributes;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Optional;

/**
 * A Publisher that handles HTTP client request tracing.
 *
 * @author graemerocher
 * @since 1.0
 */
@SuppressWarnings("PublisherImplementation")
class HttpClientTracingPublisher implements Publisher<HttpResponse<?>> {
    private static final int HTTP_SUCCESS_CODE_UPPER_LIMIT = 299;

    private final Publisher<HttpResponse<?>> publisher;
    private final HttpClientHandler<HttpRequest<?>, HttpResponse<?>> clientHandler;
    private final TraceContext.Injector<MutableHttpHeaders> injector;
    private final MutableHttpRequest<?> request;
    private final Tracer tracer;

    /**
     * Construct a publisher to handle HTTP client request tracing.
     *
     * @param publisher The response publisher
     * @param request An extended version of request that allows mutating
     * @param clientHandler The standardize way to instrument client
     * @param httpTracing HttpTracing
     */
    HttpClientTracingPublisher(
            Publisher<HttpResponse<?>> publisher,
            MutableHttpRequest<?> request,
            HttpClientHandler<HttpRequest<?>, HttpResponse<?>> clientHandler,
            HttpTracing httpTracing) {
        this.publisher = publisher;
        this.request = request;
        this.clientHandler = clientHandler;
        Tracing tracing = httpTracing.tracing();
        this.tracer = tracing.tracer();
        this.injector = tracing.propagation().injector(MutableHttpHeaders::add);
    }

    @Override
    public void subscribe(Subscriber<? super HttpResponse<?>> actual) {
        brave.Span span = clientHandler.handleSend(injector, request.getHeaders(), request);
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
                        configureAttributes(response);
                        configureSpan(span);
                        HttpStatus status = response.getStatus();
                        if (status.getCode() > HTTP_SUCCESS_CODE_UPPER_LIMIT) {
                            span.tag(AbstractOpenTracingFilter.TAG_HTTP_STATUS_CODE, String.valueOf(status.getCode()));
                        }
                        clientHandler.handleReceive(response, null, span);
                        actual.onNext(response);
                    }
                }

                @Override
                public void onError(Throwable error) {
                    try (Tracer.SpanInScope ignored = tracer.withSpanInScope(span)) {
                        configureSpan(span);
                        if (error instanceof HttpClientResponseException) {
                            HttpClientResponseException e = (HttpClientResponseException) error;
                            HttpResponse<?> response = e.getResponse();
                            configureAttributes(response);

                            clientHandler.handleReceive(response, e, span);
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

    private void configureSpan(Span span) {
        span.kind(Span.Kind.CLIENT);
        span.tag(AbstractOpenTracingFilter.TAG_METHOD, request.getMethod().name());
        String path = request.getPath();
        Optional<Object> serviceIdOptional = request.getAttribute(HttpAttributes.SERVICE_ID);
        if (serviceIdOptional.isPresent()) {
            String serviceId = serviceIdOptional.get().toString();
            if (StringUtils.isNotEmpty(serviceId) && serviceId.startsWith("/")) {
                path = StringUtils.prependUri(serviceId, path);
            }
        }
        span.tag(AbstractOpenTracingFilter.TAG_PATH, path);
    }

    private void configureAttributes(HttpResponse<?> response) {
        Optional<Object> routeTemplate = request.getAttribute(HttpAttributes.URI_TEMPLATE);
        routeTemplate.ifPresent(o -> response.setAttribute(HttpAttributes.URI_TEMPLATE, o));
        response.setAttribute(HttpAttributes.METHOD_NAME, request.getMethod().name());
    }
}
