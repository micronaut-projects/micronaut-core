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
import io.micronaut.http.filter.OncePerRequestHttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.tracing.brave.instrument.http.BraveTracingServerFilter;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracer;
import io.opentracing.propagation.Format;
import io.reactivex.Flowable;
import io.reactivex.functions.Action;
import org.reactivestreams.Publisher;

import java.util.Optional;
import java.util.function.Consumer;

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

    private static final CharSequence APPLIED = OpenTracingServerFilter.class.getName() + "-applied";

    public OpenTracingServerFilter(Tracer tracer) {
        super(tracer);
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        if( request.getAttribute(APPLIED, Boolean.class).isPresent() ) {
            return chain.proceed(request);
        }
        else {
            request.setAttribute(APPLIED, true);
            SpanContext spanContext = tracer.extract(
                    Format.Builtin.HTTP_HEADERS,
                    new HttpHeadersTextMap(request.getHeaders())
            );
            request.setAttribute(
                    TraceRequestAttributes.CURRENT_SPAN_CONTEXT,
                    spanContext
            );

            Flowable<MutableHttpResponse<?>> responsePublisher = Flowable.fromPublisher(chain.proceed(request));

            responsePublisher = responsePublisher.doOnRequest(amount -> {
                if(amount > 0) {
                    Tracer.SpanBuilder spanBuilder = newSpan(request, spanContext);
                    spanBuilder.withTag(TAG_HTTP_SERVER, true);
                    Scope scope = spanBuilder.startActive(true);
                    Span span = scope.span();
                    request.setAttribute(TraceRequestAttributes.CURRENT_SPAN, span);
                    request.setAttribute(TraceRequestAttributes.CURRENT_SCOPE, scope);

                }
            });

            responsePublisher = responsePublisher.map(response -> {
                Optional<Span> currentSpan = request.getAttribute(TraceRequestAttributes.CURRENT_SPAN, Span.class);

                currentSpan.ifPresent(span -> {
                    tracer.inject(
                            span.context(),
                            Format.Builtin.HTTP_HEADERS,
                            new HttpHeadersTextMap(response.getHeaders())
                    );

                    String spanName = resolveSpanName(request);
                    span.setOperationName(spanName);
                    setResponseTags(request, response, span);
                });

                return response;
            });
            return responsePublisher.doAfterTerminate(() -> {
                Optional<Scope> registeredScope = request.getAttribute(TraceRequestAttributes.CURRENT_SCOPE, Scope.class);
                registeredScope.ifPresent(Scope::close);
            });
        }
    }

}
