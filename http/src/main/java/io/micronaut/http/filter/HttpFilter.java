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

import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import org.reactivestreams.Publisher;

/**
 * <p>A interface for classes that can intercept and filter {@link io.micronaut.http.HttpRequest} instances and can
 * either proceed with the request or return a modified result.</p>
 * <p>
 * <p>Implementations are passed a {@link FilterChain} where the last entry in the chain in the action to be executed
 * that returns a {@link Publisher} that emits an {@link HttpResponse}</p>
 * <p>
 * <p>Each filter implements {@link Ordered} and can return an order to increase or decrease the priority of the filter</p>
 * <p>
 * <p>To modify the request filters can either wrap it (using {@link io.micronaut.http.HttpRequestWrapper} or pass it
 * along the chain as is</p>
 * <p>
 * <p>The response can be altered by returning an alternative {@link Publisher} that emits a {@link HttpResponse} or
 * by altering the publisher returned by {@link FilterChain#proceed(HttpRequest)}</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface HttpFilter extends Ordered {

    /**
     * Intercepts a {@link HttpRequest}.
     *
     * @param request The {@link HttpRequest} instance
     * @param chain   The {@link FilterChain} instance
     * @return A {@link Publisher} for the Http response
     */
    Publisher<? extends HttpResponse<?>> doFilter(HttpRequest<?> request, FilterChain chain);
}
