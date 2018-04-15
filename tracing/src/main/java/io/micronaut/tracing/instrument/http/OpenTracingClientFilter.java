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
import io.opentracing.noop.NoopTracer;
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
@Requires(missingBeans = NoopTracer.class)
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
                spanBuilder.withTag(TAG_HTTP_CLIENT, true);
                Span newSpan = spanBuilder.start();
                SpanContext newContext = newSpan.context();
                tracer.inject(
                        newContext,
                        Format.Builtin.HTTP_HEADERS,
                        new HttpHeadersTextMap(request.getHeaders())
                );
                request.setAttribute(
                        TraceRequestAttributes.CURRENT_SPAN_CONTEXT,
                        newContext
                );

                request.setAttribute(TraceRequestAttributes.CURRENT_SPAN, newSpan);
            }
        });

        return requestFlowable.map(response -> {
            Optional<Span> currentSpan = request.getAttribute(TraceRequestAttributes.CURRENT_SPAN, Span.class);
            currentSpan.ifPresent(span -> {
                setResponseTags(request, response, span);
                span.finish();
            });

            return response;
        }).onErrorResumeNext(error -> {
            if(error instanceof HttpClientResponseException) {
                HttpClientResponseException e = (HttpClientResponseException) error;
                HttpResponse<?> response = e.getResponse();
                Optional<Span> currentSpan = request.getAttribute(TraceRequestAttributes.CURRENT_SPAN, Span.class);
                currentSpan.ifPresent(span -> {
                    setResponseTags(request, response, span);
                    setErrorTags(span, error);
                    span.finish();
                });
            }
            return Flowable.error(error);
        });
    }
}
