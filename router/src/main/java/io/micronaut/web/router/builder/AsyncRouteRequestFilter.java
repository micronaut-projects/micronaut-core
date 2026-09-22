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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletionStage;

/**
 * An asynchronous filter of one route's requests, declared with
 * {@link io.micronaut.web.router.builder.HttpRouteSpec#beforeAsync(AsyncRouteRequestFilter)}. The filter chain continues when the
 * returned stage completes, so the filter must not block: it runs on the thread of the filter
 * chain, which can be the event loop.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
@FunctionalInterface
public interface AsyncRouteRequestFilter {

    /**
     * Filter the request.
     *
     * @param request The request
     * @return Completes with a response to answer the request with instead of the route, or with
     * {@code null} to proceed; completing exceptionally is handled by the error routes
     */
    CompletionStage<? extends @Nullable HttpResponse<?>> filter(HttpRequest<?> request);
}
