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
package io.micronaut.http.filter;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import org.reactivestreams.Publisher;

/**
 * An HttpServerFilter extends {@link HttpFilter} and provides the response as a {@link MutableHttpResponse}.
 *
 * @author Graeme Rocher
 * @see HttpFilter
 * @since 1.0
 */
public interface HttpServerFilter extends HttpFilter {

    /**
     * Variation of the {@link #doFilter(HttpRequest, FilterChain)} method that accepts a {@link ServerFilterChain}
     * which allows to mutate the outgoing HTTP response.
     *
     * @param request The request
     * @param chain   The chain
     * @return A {@link Publisher} that emits a {@link MutableHttpResponse}
     * @see #doFilter(HttpRequest, FilterChain)
     */
    Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain);

    @Override
    default Publisher<? extends HttpResponse<?>> doFilter(HttpRequest<?> request, FilterChain chain) {
        if (!(chain instanceof ServerFilterChain)) {
            throw new IllegalArgumentException("Passed FilterChain must be an instance of ServerFilterChain");
        }
        return doFilter(request, (ServerFilterChain) chain);
    }
}
