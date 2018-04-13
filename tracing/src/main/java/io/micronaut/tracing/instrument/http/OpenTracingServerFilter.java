/*
 * Copyright 2018 original authors
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
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.tracing.brave.instrument.http.BraveTracingServerFilter;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracer;
import io.opentracing.propagation.Format;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;

import java.util.Optional;

/**
 * An HTTP server instrumentation filter that uses Open Tracing
 *
 * @author graemerocher
 * @since 1.0
 */
@Filter(AbstractOpenTracingFilter.SERVER_PATH)
@Requires(beans = Tracer.class)
@Requires(missingBeans = NoopTracer.class)
@Requires(missingBeans = BraveTracingServerFilter.class)
public class OpenTracingServerFilter extends AbstractOpenTracingFilter implements HttpServerFilter {

    public OpenTracingServerFilter(Tracer tracer) {
        super(tracer);
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        Flowable<MutableHttpResponse<?>> responsePublisher = Flowable.fromPublisher(chain.proceed(request));

        responsePublisher = responsePublisher.doOnRequest(amount -> {
            if(amount > 0) {

                SpanContext spanContext = tracer.extract(
                        Format.Builtin.HTTP_HEADERS,
                        new HttpHeadersTextMap(request.getHeaders())
                );
                request.setAttribute(
                        TraceRequestAttributes.CURRENT_SPAN_CONTEXT,
                        spanContext
                );

                Tracer.SpanBuilder spanBuilder = newSpan(request, spanContext);

                Scope scope = spanBuilder.startActive(true);
                request.setAttribute(TraceRequestAttributes.CURRENT_SPAN, scope.span());
                request.setAttribute(TraceRequestAttributes.CURRENT_SCOPE, scope);
            }
        });

        return responsePublisher.map(response -> {
            Optional<SpanContext> spanContext = request.getAttribute(TraceRequestAttributes.CURRENT_SPAN_CONTEXT, SpanContext.class);
            spanContext.ifPresent(ctx -> {
                tracer.inject(
                        ctx,
                        Format.Builtin.HTTP_HEADERS,
                        new HttpHeadersTextMap(response.getHeaders())
                );
                Optional<Scope> scope = request.getAttribute(TraceRequestAttributes.CURRENT_SCOPE, Scope.class);
                scope.ifPresent(resolvedScope -> {
                    Span span = resolvedScope.span();
                    String spanName = resolveSpanName(request);
                    span.setOperationName(spanName);
                    setResponseTags(request, response, span);
                    resolvedScope.close();
                });
            });

            return response;
        });
    }

}
