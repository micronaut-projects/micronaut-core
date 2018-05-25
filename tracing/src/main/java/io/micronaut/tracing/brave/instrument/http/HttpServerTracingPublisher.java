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
import brave.http.HttpServerHandler;
import brave.http.HttpTracing;
import io.micronaut.http.*;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.tracing.instrument.http.AbstractOpenTracingFilter;
import io.micronaut.tracing.instrument.http.TraceRequestAttributes;
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
    private static final int HTTP_SUCCESS_CODE_UPPER_LIMIT = 299;

    private final Publisher<MutableHttpResponse<?>> publisher;
    private final HttpServerHandler<HttpRequest<?>, MutableHttpResponse<?>> serverHandler;
    private final HttpRequest<?> request;
    private final Tracer tracer;
    private final Span initialSpan;

    /**
     * Construct a publisher to handle HTTP client request tracing.
     *
     * @param publisher The response publisher
     * @param request An extended version of request that allows mutating
     * @param serverHandler The standardize way to instrument client
     * @param httpTracing HttpTracing
     * @param initialSpan The initial span
     */
    HttpServerTracingPublisher(
            Publisher<MutableHttpResponse<?>> publisher,
            HttpRequest<?> request,
            HttpServerHandler<HttpRequest<?>, MutableHttpResponse<?>> serverHandler,
            HttpTracing httpTracing,
            Span initialSpan) {
        this.publisher = publisher;
        this.request = request;
        this.initialSpan = initialSpan;
        this.serverHandler = serverHandler;
        Tracing tracing = httpTracing.tracing();
        this.tracer = tracing.tracer();
    }

    @Override
    public void subscribe(Subscriber<? super MutableHttpResponse<?>> actual) {
        Span span = initialSpan;
        Optional<Object> routeTemplate = request.getAttribute(HttpAttributes.URI_TEMPLATE);
        routeTemplate.ifPresent(o ->
                span.name(request.getMethod() + " " + o.toString())
        );
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
                        configureAttributes(response);
                        configureSpan(span);
                        HttpStatus status = response.getStatus();
                        if (status.getCode() > HTTP_SUCCESS_CODE_UPPER_LIMIT) {
                            span.tag(AbstractOpenTracingFilter.TAG_HTTP_STATUS_CODE, String.valueOf(status.getCode()));
                        }
                        serverHandler.handleSend(response, null, span);
                        actual.onNext(response);
                    }
                }

                @Override
                public void onError(Throwable error) {
                    try (Tracer.SpanInScope ignored = tracer.withSpanInScope(span)) {
                        configureSpan(span);
                        if (error instanceof HttpStatusException) {
                            int code = ((HttpStatusException) error).getStatus().getCode();
                            span.tag(AbstractOpenTracingFilter.TAG_HTTP_STATUS_CODE, String.valueOf(code));
                        } else {
                            span.tag(AbstractOpenTracingFilter.TAG_HTTP_STATUS_CODE, String.valueOf(500));
                        }
                        span.error(error);
                        span.finish();

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
        span.kind(Span.Kind.SERVER);
        span.tag(AbstractOpenTracingFilter.TAG_METHOD, request.getMethod().name());
        span.tag(AbstractOpenTracingFilter.TAG_PATH, request.getPath());
    }

    private void configureAttributes(HttpResponse<?> response) {
        Optional<Object> routeTemplate = request.getAttribute(HttpAttributes.URI_TEMPLATE);
        routeTemplate.ifPresent(o -> response.setAttribute(HttpAttributes.URI_TEMPLATE, o));
        response.setAttribute(HttpAttributes.METHOD_NAME, request.getMethod().name());
    }
}
