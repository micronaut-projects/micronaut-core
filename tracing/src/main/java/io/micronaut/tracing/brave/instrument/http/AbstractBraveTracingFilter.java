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
package io.micronaut.tracing.brave.instrument.http;

import brave.Span;
import brave.Tracer;
import brave.http.HttpTracing;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.filter.HttpFilter;
import io.micronaut.tracing.instrument.http.TraceRequestAttributes;

import java.util.Optional;

/**
 * Abstract tracing filter shared across server and client.
 *
 * @author graemerocher
 * @since 1.0
 */
abstract class AbstractBraveTracingFilter implements HttpFilter {
    protected final HttpTracing httpTracing;

    /**
     * Configure tracer in the filter for span creation and propagation across arbitrary transports.
     *
     * @param httpTracing HttpTracing
     */
    AbstractBraveTracingFilter(HttpTracing httpTracing) {
        this.httpTracing = httpTracing;
    }

    /**
     * Configures the request with the given Span.
     *
     * @param request The request
     * @param span The span
     */
    void withSpanInScope(HttpRequest<?> request, Span span) {
        request.setAttribute(TraceRequestAttributes.CURRENT_SPAN, span);
        Tracer.SpanInScope spanInScope = httpTracing.tracing().tracer().withSpanInScope(span);
        request.setAttribute(TraceRequestAttributes.CURRENT_SCOPE, spanInScope);
    }

    /**
     * Closes the scope after terminating the request.
     *
     * @param request The request
     */
    void afterTerminate(HttpRequest<?> request) {
        Optional<Tracer.SpanInScope> scope = request.removeAttribute(
                TraceRequestAttributes.CURRENT_SCOPE,
                Tracer.SpanInScope.class
        );
        scope.ifPresent(Tracer.SpanInScope::close);
    }

    /**
     * Obtain the value of current span attribute on the HTTP method.
     *
     * @param request request
     * @param response response
     * @return current span attribute
     */
    Optional<Span> configuredSpan(HttpRequest<?> request, HttpResponse<?> response) {
        Optional<Object> routeTemplate = request.getAttribute(HttpAttributes.URI_TEMPLATE);
        routeTemplate.ifPresent(o -> response.setAttribute(HttpAttributes.URI_TEMPLATE, o));
        response.setAttribute(HttpAttributes.METHOD_NAME, request.getMethodName());
        return request.getAttribute(TraceRequestAttributes.CURRENT_SPAN, Span.class);
    }
}
