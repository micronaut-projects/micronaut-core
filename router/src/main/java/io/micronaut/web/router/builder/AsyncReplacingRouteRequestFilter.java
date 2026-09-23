/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.web.router.builder;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.HttpMessage;
import io.micronaut.http.MutableHttpRequest;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletionStage;

/**
 * An asynchronous filter of one route's requests, declared with
 * {@link io.micronaut.web.router.builder.HttpRouteSpec#beforeReplacingAsync(AsyncReplacingRouteRequestFilter)}. The filter chain continues when the
 * returned stage completes, so the filter must not block: it runs on the thread of the filter
 * chain, which can be the event loop. It changes or replaces the request like a
 * {@link ReplacingRouteRequestFilter}, e.g. with a request whose body it built from the body it read.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
@FunctionalInterface
public interface AsyncReplacingRouteRequestFilter {

    /**
     * Filter the request.
     *
     * @param request The request, to change in place until the returned stage completes, see {@link ReplacingRouteRequestFilter}
     * @return Completes with a response to answer the request with instead of the route, with a
     * request to continue with instead of the request, or with {@code null} to proceed with the
     * request; completing exceptionally is handled by the error routes
     * @throws Exception An error, handled by the error routes like a controller error
     */
    CompletionStage<? extends @Nullable HttpMessage<?>> filter(MutableHttpRequest<?> request) throws Exception;
}
