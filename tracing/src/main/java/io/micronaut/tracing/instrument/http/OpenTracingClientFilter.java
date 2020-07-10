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

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.filter.ClientFilterChain;
import io.micronaut.http.filter.HttpClientFilter;
import io.micronaut.tracing.brave.instrument.http.BraveTracingClientFilter;
import io.micronaut.tracing.instrument.util.TracingPublisher;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracer;
import io.opentracing.propagation.Format;
import org.reactivestreams.Publisher;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * An HTTP client instrumentation filter that uses Open Tracing.
 *
 * @author graemerocher
 * @since 1.0
 */
@Filter(AbstractOpenTracingFilter.CLIENT_PATH)
@Requires(beans = Tracer.class)
@Requires(missingBeans = NoopTracer.class)
@Requires(missingBeans = BraveTracingClientFilter.class)
public class OpenTracingClientFilter extends AbstractOpenTracingFilter implements HttpClientFilter {

    /**
     * Initialize the open tracing client filter with tracer.
     *
     * @param tracer The tracer for span creation and configuring across arbitrary transports
     */
    public OpenTracingClientFilter(Tracer tracer) {
        super(tracer);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {
        Publisher<? extends HttpResponse<?>> requestPublisher = chain.proceed(request);
        Span activeSpan = tracer.scopeManager().activeSpan();
        SpanContext activeContext = activeSpan != null ? activeSpan.context() : null;
        Tracer.SpanBuilder spanBuilder = newSpan(request, activeContext);

        return new TracingPublisher(
                requestPublisher,
                tracer,
                spanBuilder,
                true
        ) {
            @Override
            protected void doOnSubscribe(@NonNull Span span) {
                span.setTag(TAG_HTTP_CLIENT, true);
                SpanContext spanContext = span.context();
                tracer.inject(
                        spanContext,
                        Format.Builtin.HTTP_HEADERS,
                        new HttpHeadersTextMap(request.getHeaders())
                );
                request.setAttribute(
                        TraceRequestAttributes.CURRENT_SPAN_CONTEXT,
                        spanContext
                );
                request.setAttribute(TraceRequestAttributes.CURRENT_SPAN, span);
            }

            @Override
            protected void doOnNext(@NonNull Object object, @NonNull Span span) {
                if (object instanceof HttpResponse) {
                    setResponseTags(request, (HttpResponse<?>) object, span);
                }
            }

            @Override
            protected void doOnError(@NonNull Throwable error, @NonNull Span span) {
                if (error instanceof HttpClientResponseException) {
                    HttpClientResponseException e = (HttpClientResponseException) error;
                    HttpResponse<?> response = e.getResponse();
                    setResponseTags(request, response, span);
                }
                setErrorTags(span, error);
            }
        };
    }
}
