/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.tracing.instrument.http;

import io.micronaut.http.*;
import io.micronaut.http.filter.HttpFilter;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;

import java.util.Optional;

/**
 * Abstract filter used for Open Tracing based HTTP tracing.
 *
 * @author graemerocher
 * @since 1.0
 */
public abstract class AbstractOpenTracingFilter implements HttpFilter  {
    public static final String CLIENT_PATH = "${tracing.http.client.path:/**}";
    public static final String SERVER_PATH = "${tracing.http.server.path:/**}";
    public static final String TAG_METHOD = "http.method";
    public static final String TAG_PATH = "http.path";
    public static final String TAG_ERROR = "error";
    public static final String TAG_HTTP_STATUS_CODE = "http.status_code";
    public static final String TAG_HTTP_CLIENT = "http.client";
    public static final String TAG_HTTP_SERVER = "http.server";

    private static final int HTTP_SUCCESS_CODE_UPPER_LIMIT = 299;

    protected final Tracer tracer;

    /**
     * Configure tracer in the filter for span creation and propagation across arbitrary transports.
     *
     * @param tracer The tracer
     */
    public AbstractOpenTracingFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    /**
     * Sets the response tags.
     *
     * @param request The request
     * @param response The response
     * @param span The span
     */
    protected void setResponseTags(HttpRequest<?> request, HttpResponse<?> response, Span span) {
        HttpStatus status = response.getStatus();
        int code = status.getCode();
        if (code > HTTP_SUCCESS_CODE_UPPER_LIMIT) {
            span.setTag(TAG_HTTP_STATUS_CODE, code);
            span.setTag(TAG_ERROR, status.getReason());
        }
        request.getAttribute(HttpAttributes.ERROR, Throwable.class).ifPresent(error ->
                setErrorTags(span, error)
        );
    }

    /**
     * Sets the error tags to use on the span.
     *
     * @param span The span
     * @param error The error
     */
    protected void setErrorTags(Span span, Throwable error) {
        if (error != null) {
            String message = error.getMessage();
            if (message == null) {
                message = error.getClass().getSimpleName();
            }
            span.setTag(TAG_ERROR, message);
        }
    }

    /**
     * Resolve the span name to use for the request.
     *
     * @param request The request
     * @return The span name
     */
    protected String resolveSpanName(HttpRequest<?> request) {
        Optional<String> route = request.getAttribute(HttpAttributes.URI_TEMPLATE, String.class);
        return route.map(s -> request.getMethodName() + " " + s).orElse(request.getMethodName() + " " + request.getPath());
    }

    /**
     * Creates a new span for the given request and span context.
     *
     * @param request The request
     * @param spanContext The span context
     * @return The span builder
     */
    protected Tracer.SpanBuilder newSpan(HttpRequest<?> request, SpanContext spanContext) {
        String spanName = resolveSpanName(request);
        Tracer.SpanBuilder spanBuilder = tracer.buildSpan(
                spanName
        ).asChildOf(spanContext);

        spanBuilder.withTag(TAG_METHOD, request.getMethodName());
        String path = request.getPath();
        spanBuilder.withTag(TAG_PATH, path);
        return spanBuilder;
    }
}
