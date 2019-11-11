/*
 * Copyright 2017-2019 original authors
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

import brave.http.HttpClientHandler;
import brave.http.HttpClientRequest;
import brave.http.HttpClientResponse;
import brave.http.HttpTracing;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.ClientFilterChain;
import io.micronaut.http.filter.HttpClientFilter;
import io.micronaut.tracing.instrument.http.AbstractOpenTracingFilter;
import org.reactivestreams.Publisher;

/**
 * Instruments outgoing HTTP requests.
 *
 * @author graemerocher
 * @since 1.0
 */
@Filter(AbstractOpenTracingFilter.CLIENT_PATH)
@Requires(beans = HttpClientHandler.class)
public class BraveTracingClientFilter extends AbstractBraveTracingFilter implements HttpClientFilter {

    private final HttpClientHandler<HttpClientRequest, HttpClientResponse> clientHandler;

    /**
     * Initialize tracing filter with clientHandler and httpTracing.
     *
     * @param clientHandler The standardize way to instrument http client
     * @param httpTracing   The tracer for creation of span
     */
    public BraveTracingClientFilter(HttpClientHandler<HttpClientRequest, HttpClientResponse> clientHandler, HttpTracing httpTracing) {
        super(httpTracing);
        this.clientHandler = clientHandler;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {
        Publisher<? extends HttpResponse<?>> requestPublisher = chain.proceed(request);
        return new HttpClientTracingPublisher(
                (Publisher<HttpResponse<?>>) requestPublisher,
                request,
                clientHandler,
                httpTracing);
    }
}
