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
 * request and response filters are implemented with the variants that can replace the request or
 * the response: a filter that does not replace it is one that returns {@code null}. Each adds a
 * {@link FilterRegistration}, whose executor its {@link FilterSpec} chooses.
 *
 * @param <S> The type that declares the filters
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
sealed interface ContextFilterSpec<S extends RouteFilterSpec<S>> extends RouteFilterSpec<S> permits DefaultHttpRouteSpec, DefaultHttpRouteGroup, DefaultServerFilterSpec {

    /**
     * Add a filter.
     *
     * @param filter The filter
     * @return Its spec
     */
    FilterSpec<S> addFilter(FilterRegistration filter);

    @Override
    default FilterSpec<S> beforeReplacing(ContextReplacingRouteRequestFilter filter) {
        return addFilter(FilterRegistration.before(filter));
    }

    @Override
    default FilterSpec<S> beforeReplacingAsync(AsyncContextReplacingRouteRequestFilter filter) {
        return addFilter(FilterRegistration.beforeAsync(filter));
    }

    @Override
    default FilterSpec<S> afterReplacing(ContextReplacingRouteResponseFilter filter) {
        return addFilter(FilterRegistration.after(filter));
    }

    @Override
    default FilterSpec<S> afterReplacingAsync(AsyncContextReplacingRouteResponseFilter filter) {
        return addFilter(FilterRegistration.afterAsync(filter));
    }

    @Override
    default FilterSpec<S> beforeReplacing(ReplacingRouteRequestFilter filter) {
        Objects.requireNonNull(filter, "filter");
        return beforeReplacing((ContextReplacingRouteRequestFilter) (request, propagatedContext) -> filter.filter(request));
    }

    @Override
    default FilterSpec<S> beforeReplacingAsync(AsyncReplacingRouteRequestFilter filter) {
        Objects.requireNonNull(filter, "filter");
        return beforeReplacingAsync((AsyncContextReplacingRouteRequestFilter) (request, propagatedContext) -> filter.filter(request));
    }

    @Override
    default FilterSpec<S> before(RouteRequestFilter filter) {
        Objects.requireNonNull(filter, "filter");
        return beforeReplacing((ContextReplacingRouteRequestFilter) (request, propagatedContext) -> {
            filter.filter(request);
            return null;
        });
    }

    @Override
    default FilterSpec<S> beforeAsync(AsyncRouteRequestFilter filter) {
        Objects.requireNonNull(filter, "filter");
        return beforeReplacingAsync((AsyncContextReplacingRouteRequestFilter) (request, propagatedContext) ->
            Objects.requireNonNull(filter.filter(request), "The asynchronous request filter returned no stage").thenApply(ignored -> null));
    }

    @Override
    default FilterSpec<S> before(ContextRouteRequestFilter filter) {
        Objects.requireNonNull(filter, "filter");
        return beforeReplacing((ContextReplacingRouteRequestFilter) (request, propagatedContext) -> {
            filter.filter(request, propagatedContext);
            return null;
        });
    }

    @Override
    default FilterSpec<S> beforeAsync(AsyncContextRouteRequestFilter filter) {
        Objects.requireNonNull(filter, "filter");
        return beforeReplacingAsync((AsyncContextReplacingRouteRequestFilter) (request, propagatedContext) ->
            Objects.requireNonNull(filter.filter(request, propagatedContext), "The asynchronous request filter returned no stage")
                .thenApply(ignored -> null));
    }

    @Override
    default FilterSpec<S> after(RouteResponseFilter filter) {
        Objects.requireNonNull(filter, "filter");
        return after((ContextRouteResponseFilter) (request, response, propagatedContext) -> filter.filter(request, response));
    }

    @Override
    default FilterSpec<S> afterAsync(AsyncRouteResponseFilter filter) {
        Objects.requireNonNull(filter, "filter");
        return afterAsync((AsyncContextRouteResponseFilter) (request, response, propagatedContext) -> filter.filter(request, response));
    }

    @Override
    default FilterSpec<S> after(ContextRouteResponseFilter filter) {
        Objects.requireNonNull(filter, "filter");
        return afterReplacing((ContextReplacingRouteResponseFilter) (request, response, propagatedContext) -> {
            filter.filter(request, response, propagatedContext);
            return null;
        });
    }

    @Override
    default FilterSpec<S> afterAsync(AsyncContextRouteResponseFilter filter) {
        Objects.requireNonNull(filter, "filter");
        return afterReplacingAsync((AsyncContextReplacingRouteResponseFilter) (request, response, propagatedContext) ->
            Objects.requireNonNull(filter.filter(request, response, propagatedContext),
                "The asynchronous response filter returned no stage").thenApply(ignored -> null));
    }

    @Override
    default FilterSpec<S> afterReplacing(ReplacingRouteResponseFilter filter) {
        Objects.requireNonNull(filter, "filter");
        return afterReplacing((ContextReplacingRouteResponseFilter) (request, response, propagatedContext) -> filter.filter(request, response));
    }

    @Override
    default FilterSpec<S> afterReplacingAsync(AsyncReplacingRouteResponseFilter filter) {
        Objects.requireNonNull(filter, "filter");
        return afterReplacingAsync((AsyncContextReplacingRouteResponseFilter) (request, response, propagatedContext) -> filter.filter(request, response));
    }
}
