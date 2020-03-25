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
package io.micronaut.tracing.instrument.http;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.filter.ServerFilterPhase;
import io.micronaut.tracing.brave.instrument.http.BraveTracingServerFilter;
import io.micronaut.tracing.instrument.util.TracingPublisher;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracer;
import io.opentracing.propagation.Format;
import org.reactivestreams.Publisher;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * An HTTP server instrumentation filter that uses Open Tracing.
 *
 * @author graemerocher
 * @since 1.0
 */
@Filter(AbstractOpenTracingFilter.SERVER_PATH)
@Requires(beans = Tracer.class)
@Requires(missingBeans = NoopTracer.class)
@Requires(missingBeans = BraveTracingServerFilter.class)
public class OpenTracingServerFilter extends AbstractOpenTracingFilter implements HttpServerFilter {

    private static final CharSequence APPLIED = OpenTracingServerFilter.class.getName() + "-applied";

    /**
     * Creates an HTTP server instrumentation filter.
     *
     * @param tracer For span creation and propagation across transport
     */
    public OpenTracingServerFilter(Tracer tracer) {
        super(tracer);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        Publisher<MutableHttpResponse<?>> responsePublisher = chain.proceed(request);
        if (request.getAttribute(APPLIED, Boolean.class).isPresent()) {
            return responsePublisher;
        } else {
            request.setAttribute(APPLIED, true);
            SpanContext spanContext = tracer.extract(
                    Format.Builtin.HTTP_HEADERS,
                    new HttpHeadersTextMap(request.getHeaders())
            );
            request.setAttribute(
                    TraceRequestAttributes.CURRENT_SPAN_CONTEXT,
                    spanContext
            );

            Tracer.SpanBuilder spanBuilder = newSpan(request, spanContext);
            return new TracingPublisher(responsePublisher, tracer, spanBuilder) {
                @Override
                protected void doOnSubscribe(@NonNull Span span) {
                    span.setTag(TAG_HTTP_SERVER, true);
                    request.setAttribute(TraceRequestAttributes.CURRENT_SPAN, span);

                }

                @Override
                protected void doOnNext(@NonNull Object object, @NonNull Span span) {
                    if (object instanceof HttpResponse) {
                        HttpResponse<?> response = (HttpResponse<?>) object;
                        tracer.inject(
                                span.context(),
                                Format.Builtin.HTTP_HEADERS,
                                new HttpHeadersTextMap(response.getHeaders())
                        );

                        String spanName = resolveSpanName(request);
                        span.setOperationName(spanName);
                        setResponseTags(request, response, span);
                    }
                }
            };

        }
    }

    @Override
    public int getOrder() {
        return ServerFilterPhase.TRACING.order();
    }
}
