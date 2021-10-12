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
package io.micronaut.http.filter;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import org.reactivestreams.Publisher;

/**
 * An HttpClientFilter extends {@link HttpFilter} and allows the passed request to be mutated. HttpClientFilter are
 * specific to HTTP client requests and are not processed by the server.
 *
 * @author Graeme Rocher
 * @see HttpFilter
 * @since 1.0
 */
public interface HttpClientFilter extends HttpFilter {

    /**
     * A variation of {@link HttpFilter#doFilter(HttpRequest, FilterChain)} that receives a {@link MutableHttpRequest}
     * allowing the request to be modified.
     *
     * @param request The request
     * @param chain   The filter chain
     * @return The publisher of the response
     * @see HttpFilter
     */
    Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain);

    @Override
    default Publisher<? extends HttpResponse<?>> doFilter(HttpRequest<?> request, FilterChain chain) {
        if (!(request instanceof MutableHttpRequest)) {
            throw new IllegalArgumentException("Passed request must be an instance of " + MutableHttpRequest.class.getName());
        }
        if (!(chain instanceof ClientFilterChain)) {
            throw new IllegalArgumentException("Passed chain must be an instance of " + ClientFilterChain.class.getName());
        }

        return doFilter((MutableHttpRequest) request, (ClientFilterChain) chain);
    }
}
