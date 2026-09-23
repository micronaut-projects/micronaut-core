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
import io.micronaut.http.MutableHttpRequest;

import java.util.concurrent.CompletionStage;

/**
 * An asynchronous filter of one route's requests that changes the request in place, declared with
 * {@link HttpRouteSpec#beforeAsync(AsyncRouteRequestFilter)}: the filter chain continues with the
 * request when the returned stage completes, so the filter must not block: it runs on the thread
 * of the filter chain, which can be the event loop. A filter that answers or replaces the request
 * is an {@link AsyncReplacingRouteRequestFilter}.
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
     * @param request The request, to change in place until the returned stage completes, see {@link RouteRequestFilter}
     * @return Completes when the request continues; completing exceptionally is handled by the error routes
     * @throws Exception An error, handled by the error routes like a controller error
     */
    CompletionStage<?> filter(MutableHttpRequest<?> request) throws Exception;
}
