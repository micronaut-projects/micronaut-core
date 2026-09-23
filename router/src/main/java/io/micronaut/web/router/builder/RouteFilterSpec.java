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

/**
 * Filter methods of handler routes: the filters of one route, see {@link HttpRouteSpec}, and of
 * every route of a group, see {@link HttpRouteGroup}. A filter applies to the routes of what
 * declares it: the filter of a route to that route, the filter of a group to every route declared
 * in the group.
 *
 * <p>Request filters run in the order they are declared, and so do response filters, after the
 * route. A response filter also filters a response a request filter answered with instead of the
 * route, e.g. to add headers to a rejected request, when the response filter was declared on the
 * same route or group as the request filter or on an enclosing group.</p>
 *
 * <p>Like a filter method, a filter runs with the propagated context of the filter chain in scope,
 * e.g. the MDC context an application filter added; to change it, see the variants that receive a
 * {@link io.micronaut.core.propagation.MutablePropagatedContext}.</p>
 *
 * @param <S> The type that declares the filters
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface RouteFilterSpec<S extends RouteFilterSpec<S>> {

    /**
     * Filter the requests, like a {@code @RequestFilter} method that applies to these routes only.
     * The filters of a route and of its groups run after the application's filters, closest to the
     * route: the filters of the outer group first, then those of the inner groups, then those of
     * the route, each in the order they are declared.
     *
     * @param filter The filter, which can answer the request instead of the route
     * @return This
     */
    S before(RouteRequestFilter filter);

    /**
     * Filter the responses, like a {@code @ResponseFilter} method that applies to these routes only.
     * Response filters run after the route and before the application's response filters: the
     * filters of the route first, then those of the inner groups, then those of the outer group,
     * each in the order they are declared.
     *
     * @param filter The filter
     * @return This
     */
    S after(RouteResponseFilter filter);

    /**
     * Filter the requests on the named executor, like a {@code @RequestFilter} method annotated
     * {@code @ExecuteOn}: use it for a filter that blocks, e.g. on a database. The filter chain
     * continues on that executor.
     *
     * @param executorName The name of the executor, e.g. {@code TaskExecutors.BLOCKING}
     * @param filter       The filter, which can answer the request instead of the route
     * @return This
     */
    S before(String executorName, RouteRequestFilter filter);

    /**
     * Filter the responses on the named executor, like a {@code @ResponseFilter} method annotated
     * {@code @ExecuteOn}.
     *
     * @param executorName The name of the executor, e.g. {@code TaskExecutors.BLOCKING}
     * @param filter       The filter
     * @return This
     */
    S after(String executorName, RouteResponseFilter filter);

    /**
     * Filter the requests asynchronously, like a {@code @RequestFilter} method returning a
     * {@code CompletionStage}: the filter chain continues when the stage completes.
     *
     * @param filter The filter, which can answer the request instead of the route
     * @return This
     */
    S beforeAsync(AsyncRouteRequestFilter filter);

    /**
     * Filter the responses asynchronously: the filter chain continues when the stage completes.
     *
     * @param filter The filter
     * @return This
     */
    S afterAsync(AsyncRouteResponseFilter filter);

    /**
     * Filter the requests with a filter that changes the propagated context, like a
     * {@code @RequestFilter} method with a {@code MutablePropagatedContext} parameter: what it adds
     * is in scope for the next filters, the handler, the error routes and the response filters.
     * Otherwise the same as {@link #before(RouteRequestFilter)}.
     *
     * @param filter The filter, which can answer the request instead of the route
     * @return This
     * @see ContextRouteRequestFilter
     */
    S before(ContextRouteRequestFilter filter);

    /**
     * Filter the requests on the named executor with a filter that changes the propagated context.
     *
     * @param executorName The name of the executor, e.g. {@code TaskExecutors.BLOCKING}
     * @param filter       The filter, which can answer the request instead of the route
     * @return This
     * @see #before(String, RouteRequestFilter)
     * @see ContextRouteRequestFilter
     */
    S before(String executorName, ContextRouteRequestFilter filter);

    /**
     * Filter the requests asynchronously with a filter that changes the propagated context until
     * its stage completes.
     *
     * @param filter The filter, which can answer the request instead of the route
     * @return This
     * @see #beforeAsync(AsyncRouteRequestFilter)
     * @see AsyncContextRouteRequestFilter
     */
    S beforeAsync(AsyncContextRouteRequestFilter filter);

    /**
     * Filter the responses with a filter that changes the propagated context of the response
     * filters after it, like a {@code @ResponseFilter} method with a
     * {@code MutablePropagatedContext} parameter.
     *
     * @param filter The filter
     * @return This
     * @see #after(RouteResponseFilter)
     * @see ContextRouteResponseFilter
     */
    S after(ContextRouteResponseFilter filter);

    /**
     * Filter the responses on the named executor with a filter that changes the propagated context.
     *
     * @param executorName The name of the executor, e.g. {@code TaskExecutors.BLOCKING}
     * @param filter       The filter
     * @return This
     * @see #after(String, RouteResponseFilter)
     * @see ContextRouteResponseFilter
     */
    S after(String executorName, ContextRouteResponseFilter filter);

    /**
     * Filter the responses asynchronously with a filter that changes the propagated context until
     * its stage completes.
     *
     * @param filter The filter
     * @return This
     * @see #afterAsync(AsyncRouteResponseFilter)
     * @see AsyncContextRouteResponseFilter
     */
    S afterAsync(AsyncContextRouteResponseFilter filter);
}
