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
package org.particleframework.http.client;

import org.particleframework.core.type.Argument;
import org.particleframework.http.HttpRequest;
import org.particleframework.http.HttpResponse;
import org.particleframework.http.MutableHttpRequest;
import org.reactivestreams.Publisher;

/**
 * A non-blocking HTTP client interface designed around the Particle API and Reactive Streams.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface HttpClient {

    /**
     * Perform an HTTP request for the given request object emitting the full HTTP response from returned {@link Publisher}
     *
     * @param request The {@link HttpRequest} to execute
     * @param <I> The request body type
     * @param <O> The response body type
     * @return A {@link Publisher} that emits the full {@link HttpResponse} object
     */
    <I,O> Publisher<HttpResponse<O>> exchange(HttpRequest<I> request);
    /**
     * Perform an HTTP request for the given request object emitting the full HTTP response from returned {@link Publisher} and converting
     * the response body to the specified type
     *
     * @param request The {@link HttpRequest} to execute
     * @param bodyType The body type
     * @param <I> The request body type
     * @param <O> The response body type
     * @return A {@link Publisher} that emits the full {@link HttpResponse} object
     */
    <I,O> Publisher<HttpResponse<O>> exchange(HttpRequest<I> request, Argument<O> bodyType);
    /**
     * Perform an HTTP request for the given request object emitting the full HTTP response from returned {@link Publisher} and converting
     * the response body to the specified type
     *
     * @param request The {@link HttpRequest} to execute
     * @param bodyType The body type
     * @param <I> The request body type
     * @param <O> The response body type
     * @return A {@link Publisher} that emits the full {@link HttpResponse} object
     */
    default <I,O> Publisher<HttpResponse<O>> exchange(HttpRequest<I> request, Class<O> bodyType) {
        return exchange(request, Argument.of(bodyType));
    }
}
