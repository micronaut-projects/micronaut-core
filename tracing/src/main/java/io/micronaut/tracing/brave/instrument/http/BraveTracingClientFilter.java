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
package io.micronaut.tracing.brave.instrument.http;

import brave.Span;
import brave.http.HttpClientHandler;
import brave.http.HttpTracing;
import brave.propagation.TraceContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.*;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.filter.ClientFilterChain;
import io.micronaut.http.filter.HttpClientFilter;
import io.micronaut.tracing.instrument.http.AbstractOpenTracingFilter;
import io.micronaut.tracing.instrument.http.TraceRequestAttributes;
import io.reactivex.Flowable;
import io.reactivex.functions.Consumer;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;

import java.util.Optional;

/**
 * Instruments outgoing HTTP requests
 *
 * @author graemerocher
 * @since 1.0
 */
@Filter(AbstractOpenTracingFilter.CLIENT_PATH)
@Requires(beans = HttpClientHandler.class)
public class BraveTracingClientFilter extends AbstractBraveTracingFilter implements HttpClientFilter {

    private final HttpClientHandler<HttpRequest<?>, HttpResponse<?>> clientHandler;
    private final TraceContext.Injector<MutableHttpHeaders> injector;

    public BraveTracingClientFilter(HttpClientHandler<HttpRequest<?>, HttpResponse<?>> clientHandler, HttpTracing httpTracing) {
        super(httpTracing);
        this.clientHandler = clientHandler;
        this.injector = httpTracing.tracing().propagation().injector(MutableHttpHeaders::add);
    }

    @Override
    public Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {
        Flowable<? extends HttpResponse<?>> requestPublisher = Flowable.fromPublisher(chain.proceed(request));
        requestPublisher = requestPublisher.doOnRequest( amount -> {
            if(amount > 0) {
                Span span = clientHandler.handleSend(injector, request.getHeaders(), request);
                request.setAttribute(TraceRequestAttributes.CURRENT_SPAN, span);
            }
        });

        return requestPublisher.map(response -> {
            Optional<Span> span = configuredSpan(request, response);
            span.ifPresent(s -> clientHandler.handleReceive(response, null, s));
            return response;
        }).onErrorResumeNext(error -> {
            if(error instanceof HttpClientResponseException) {
                HttpClientResponseException e = (HttpClientResponseException) error;
                HttpResponse<?> response = e.getResponse();
                Optional<Span> span = configuredSpan(request, response);
                span.ifPresent(s -> clientHandler.handleReceive(response, e, s));
            }

            return Flowable.error(error);
        });
    }
}
