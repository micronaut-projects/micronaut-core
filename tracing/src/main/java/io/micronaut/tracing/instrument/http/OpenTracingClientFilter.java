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
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.filter.ClientFilterChain;
import io.micronaut.http.filter.HttpClientFilter;
import io.micronaut.tracing.brave.instrument.http.BraveTracingClientFilter;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;

import java.util.Optional;

/**
 * An HTTP client instrumentation filter that uses Open Tracing
 *
 * @author graemerocher
 * @since 1.0
 */
@Filter(AbstractOpenTracingFilter.CLIENT_PATH)
@Requires(beans = Tracer.class)
@Requires(missingBeans = BraveTracingClientFilter.class)
public class OpenTracingClientFilter extends AbstractOpenTracingFilter implements HttpClientFilter {

    public OpenTracingClientFilter(Tracer tracer) {
        super(tracer);
    }

    @Override
    public Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {
        Flowable<? extends HttpResponse<?>> requestFlowable = Flowable.fromPublisher(chain.proceed(request));

        requestFlowable = requestFlowable.doOnRequest(amount -> {
            if(amount > 0) {

                Scope activeSpan = tracer.scopeManager().active();
                SpanContext activeContext = activeSpan != null ? activeSpan.span().context() : null;
                Tracer.SpanBuilder spanBuilder = newSpan(request, activeContext);
                Scope newScope = spanBuilder.startActive(true);
                SpanContext newContext = newScope.span().context();
                tracer.inject(
                        newContext,
                        Format.Builtin.HTTP_HEADERS,
                        new HttpHeadersTextMap(request.getHeaders())
                );
                request.setAttribute(
                        TraceRequestAttributes.CURRENT_SPAN_CONTEXT,
                        newContext
                );

                request.setAttribute(TraceRequestAttributes.CURRENT_SPAN, newScope.span());
                request.setAttribute(TraceRequestAttributes.CURRENT_SCOPE, newScope);
            }
        });

        return requestFlowable.map(response -> {
            Optional<SpanContext> spanContext = request.getAttribute(TraceRequestAttributes.CURRENT_SPAN_CONTEXT, SpanContext.class);
            spanContext.ifPresent(ctx -> {
                Optional<Scope> scope = request.getAttribute(TraceRequestAttributes.CURRENT_SCOPE, Scope.class);
                scope.ifPresent(resolvedScope -> {
                    Span span = resolvedScope.span();
                    setResponseTags(request, response, span);
                    resolvedScope.close();
                });
            });

            return response;
        }).onErrorResumeNext(error -> {
            if(error instanceof HttpClientResponseException) {
                HttpClientResponseException e = (HttpClientResponseException) error;
                HttpResponse<?> response = e.getResponse();
                Optional<Scope> scope = request.getAttribute(TraceRequestAttributes.CURRENT_SCOPE, Scope.class);
                scope.ifPresent(resolvedScope -> {
                    Span span = resolvedScope.span();
                    setResponseTags(request, response, span);
                    setErrorTags(span, error);
                    resolvedScope.close();
                });
            }

            return Flowable.error(error);
        });
    }
}
