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

import io.micronaut.core.annotation.Internal;

import java.util.Objects;

/**
 * The filter methods of a {@link RouteFilterSpec} implemented with the variants that receive the
 * propagated context: a filter that does not change the context is one that ignores it. The
 * response filters are implemented with the variants that can replace the response: a filter
 * that does not replace it is one that returns {@code null}.
 *
 * @param <S> The type that declares the filters
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
interface ContextFilterSpec<S extends RouteFilterSpec<S>> extends RouteFilterSpec<S> {

    @Override
    default S before(RouteRequestFilter filter) {
        Objects.requireNonNull(filter, "filter");
        return before((ContextRouteRequestFilter) (request, propagatedContext) -> filter.filter(request));
    }

    @Override
    default S before(String executorName, RouteRequestFilter filter) {
        Objects.requireNonNull(filter, "filter");
        return before(executorName, (ContextRouteRequestFilter) (request, propagatedContext) -> filter.filter(request));
    }

    @Override
    default S beforeAsync(AsyncRouteRequestFilter filter) {
        Objects.requireNonNull(filter, "filter");
        return beforeAsync((AsyncContextRouteRequestFilter) (request, propagatedContext) -> filter.filter(request));
    }

    @Override
    default S after(RouteResponseFilter filter) {
        Objects.requireNonNull(filter, "filter");
        return after((ContextRouteResponseFilter) (request, response, propagatedContext) -> filter.filter(request, response));
    }

    @Override
    default S after(String executorName, RouteResponseFilter filter) {
        Objects.requireNonNull(filter, "filter");
        return after(executorName, (ContextRouteResponseFilter) (request, response, propagatedContext) -> filter.filter(request, response));
    }

    @Override
    default S afterAsync(AsyncRouteResponseFilter filter) {
        Objects.requireNonNull(filter, "filter");
        return afterAsync((AsyncContextRouteResponseFilter) (request, response, propagatedContext) -> filter.filter(request, response));
    }

    @Override
    default S after(ContextRouteResponseFilter filter) {
        Objects.requireNonNull(filter, "filter");
        return afterReplacing((ContextReplacingRouteResponseFilter) (request, response, propagatedContext) -> {
            filter.filter(request, response, propagatedContext);
            return null;
        });
    }

    @Override
    default S after(String executorName, ContextRouteResponseFilter filter) {
        Objects.requireNonNull(filter, "filter");
        return afterReplacing(executorName, (ContextReplacingRouteResponseFilter) (request, response, propagatedContext) -> {
            filter.filter(request, response, propagatedContext);
            return null;
        });
    }

    @Override
    default S afterAsync(AsyncContextRouteResponseFilter filter) {
        Objects.requireNonNull(filter, "filter");
        return afterReplacingAsync((AsyncContextReplacingRouteResponseFilter) (request, response, propagatedContext) ->
            Objects.requireNonNull(filter.filter(request, response, propagatedContext),
                "The asynchronous response filter returned no stage").thenApply(ignored -> null));
    }

    @Override
    default S afterReplacing(ReplacingRouteResponseFilter filter) {
        Objects.requireNonNull(filter, "filter");
        return afterReplacing((ContextReplacingRouteResponseFilter) (request, response, propagatedContext) -> filter.filter(request, response));
    }

    @Override
    default S afterReplacing(String executorName, ReplacingRouteResponseFilter filter) {
        Objects.requireNonNull(filter, "filter");
        return afterReplacing(executorName, (ContextReplacingRouteResponseFilter) (request, response, propagatedContext) -> filter.filter(request, response));
    }

    @Override
    default S afterReplacingAsync(AsyncReplacingRouteResponseFilter filter) {
        Objects.requireNonNull(filter, "filter");
        return afterReplacingAsync((AsyncContextReplacingRouteResponseFilter) (request, response, propagatedContext) -> filter.filter(request, response));
    }
}
