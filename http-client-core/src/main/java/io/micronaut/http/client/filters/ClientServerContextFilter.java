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
package io.micronaut.http.client.filters;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.filter.ClientFilterChain;
import io.micronaut.http.filter.HttpClientFilter;
import org.reactivestreams.Publisher;

/**
 * A client filter that propagates the request context.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
public class ClientServerContextFilter implements HttpClientFilter {

    private final HttpRequest<?> parentRequest;

    /**
     * Default constructor.
     *
     * @param parentRequest The parent request
     */
    public ClientServerContextFilter(HttpRequest<?> parentRequest) {
        this.parentRequest = parentRequest;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {
        final Publisher<? extends HttpResponse<?>> publisher = chain.proceed(request);
        return new ClientServerRequestTracingPublisher(parentRequest, publisher);
    }
}
