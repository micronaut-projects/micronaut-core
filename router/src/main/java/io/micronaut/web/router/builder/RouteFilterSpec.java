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
 * Filter methods of handler routes: the filters of one route, see {@link HttpRouteSpec}, of
 * every route of a group, see {@link HttpRouteGroup}, and of a server filter, see
 * {@link ServerFilterSpec}. What declares a filter decides which requests it filters and when it
 * runs among the other filters: the filter of a route filters the requests of that route, the
 * filter of a group those of every route declared in the group, both after the server filters,
 * and a server filter the requests its patterns match, ordered with the filter beans.
 *
 * <p>Request filters run in the order they are declared, and so do response filters, after the
 * route. A response filter also filters a response a request filter answered with instead of the
 * route, e.g. to add headers to a rejected request, when the response filter was declared on the
 * same route or group as the request filter or on an enclosing group.</p>
 *
 * <p>The request and the response filters come in the same four families, so that their lambdas
 * are never ambiguous: {@code before} and {@code after} filters return nothing and change the
 * request or the response in place, like a filter method with a {@code MutableHttpRequest} or
 * {@code MutableHttpResponse} parameter that returns nothing; {@code beforeReplacing} and
 * {@code afterReplacing} filters can also replace it, like a filter method returning a request or
 * a response: a request filter answers the request instead of the route with the response it
 * returns, or continues with the request it returns, e.g. with another method or body, see
 * {@link ReplacingRouteRequestFilter}. Each has a variant on a named executor, an asynchronous
 * variant, and variants that change the propagated context.</p>
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
public sealed interface RouteFilterSpec<S extends RouteFilterSpec<S>> permits HttpRouteSpec, HttpRouteGroup, ServerFilterSpec, ContextFilterSpec {

    /**
     * Filter the requests, like a {@code @RequestFilter} method that returns nothing. The filters
     * of a route and of its groups run after the server filters, closest to the route: the filters
     * of the outer group first, then those of the inner groups, then those of the route, each in
     * the order they are declared.
     *
     * @param filter The filter, which can change the request in place
     * @return This
     * @see RouteRequestFilter
     */
    S before(RouteRequestFilter filter);

    /**
     * Filter the requests on the named executor, like a {@code @RequestFilter} method annotated
     * {@code @ExecuteOn}: use it for a filter that blocks, e.g. on a database. The filter chain
     * continues on that executor.
     *
     * @param executorName The name of the executor, e.g. {@code TaskExecutors.BLOCKING}
     * @param filter       The filter, which can change the request in place
     * @return This
     */
    S before(String executorName, RouteRequestFilter filter);

    /**
     * Filter the requests asynchronously, like a {@code @RequestFilter} method returning a
     * {@code CompletionStage}: the filter chain continues when the stage completes.
     *
     * @param filter The filter, which can change the request in place until its stage completes
     * @return This
     * @see AsyncRouteRequestFilter
     */
    S beforeAsync(AsyncRouteRequestFilter filter);

    /**
     * Filter the requests with a filter that changes the propagated context, like a
     * {@code @RequestFilter} method with a {@code MutablePropagatedContext} parameter: what it adds
     * is in scope for the next filters, the handler, the error routes and the response filters.
     * Otherwise the same as {@link #before(RouteRequestFilter)}.
     *
     * @param filter The filter, which can change the request and the propagated context in place
     * @return This
     * @see ContextRouteRequestFilter
     */
    S before(ContextRouteRequestFilter filter);

    /**
     * Filter the requests on the named executor with a filter that changes the propagated context.
     *
     * @param executorName The name of the executor, e.g. {@code TaskExecutors.BLOCKING}
     * @param filter       The filter, which can change the request and the propagated context in place
     * @return This
     * @see #before(String, RouteRequestFilter)
     * @see ContextRouteRequestFilter
     */
    S before(String executorName, ContextRouteRequestFilter filter);

    /**
     * Filter the requests asynchronously with a filter that changes the propagated context until
     * its stage completes.
     *
     * @param filter The filter, which can change the request and the propagated context in place
     * @return This
     * @see #beforeAsync(AsyncRouteRequestFilter)
     * @see AsyncContextRouteRequestFilter
     */
    S beforeAsync(AsyncContextRouteRequestFilter filter);

    /**
     * Filter the requests with a filter that can answer the request or replace it, like a
     * {@code @RequestFilter} method returning a response or a request: a response it returns
     * answers the request instead of the route, a request it returns is the request the filters
     * after it and the route see, and {@code null} proceeds with the request it was given.
     * Otherwise the same as {@link #before(RouteRequestFilter)}, which takes a filter that returns
     * nothing, so that its lambdas are never ambiguous.
     *
     * @param filter The filter, which can answer the request instead of the route, or change or replace the request
     * @return This
     * @see ReplacingRouteRequestFilter
     */
    S beforeReplacing(ReplacingRouteRequestFilter filter);

    /**
     * Filter the requests on the named executor with a filter that can answer or replace the request.
     *
     * @param executorName The name of the executor, e.g. {@code TaskExecutors.BLOCKING}
     * @param filter       The filter, which can answer the request instead of the route, or change or replace the request
     * @return This
     * @see #before(String, RouteRequestFilter)
     * @see ReplacingRouteRequestFilter
     */
    S beforeReplacing(String executorName, ReplacingRouteRequestFilter filter);

    /**
     * Filter the requests asynchronously with a filter that can answer or replace the request: the
     * filter chain continues when the stage completes, with what it completes with.
     *
     * @param filter The filter, which can answer the request instead of the route, or change or replace the request
     * @return This
     * @see #beforeAsync(AsyncRouteRequestFilter)
     * @see AsyncReplacingRouteRequestFilter
     */
    S beforeReplacingAsync(AsyncReplacingRouteRequestFilter filter);

    /**
     * Filter the requests with a filter that can answer or replace the request and changes the
     * propagated context.
     *
     * @param filter The filter, which can answer the request instead of the route, or change or replace the request
     * @return This
     * @see #beforeReplacing(ReplacingRouteRequestFilter)
     * @see ContextReplacingRouteRequestFilter
     */
    S beforeReplacing(ContextReplacingRouteRequestFilter filter);

    /**
     * Filter the requests on the named executor with a filter that can answer or replace the
     * request and changes the propagated context.
     *
     * @param executorName The name of the executor, e.g. {@code TaskExecutors.BLOCKING}
     * @param filter       The filter, which can answer the request instead of the route, or change or replace the request
     * @return This
     * @see #beforeReplacing(String, ReplacingRouteRequestFilter)
     * @see ContextReplacingRouteRequestFilter
     */
    S beforeReplacing(String executorName, ContextReplacingRouteRequestFilter filter);

    /**
     * Filter the requests asynchronously with a filter that can answer or replace the request and
     * changes the propagated context until its stage completes.
     *
     * @param filter The filter, which can answer the request instead of the route, or change or replace the request
     * @return This
     * @see #beforeReplacingAsync(AsyncReplacingRouteRequestFilter)
     * @see AsyncContextReplacingRouteRequestFilter
     */
    S beforeReplacingAsync(AsyncContextReplacingRouteRequestFilter filter);

    /**
     * Filter the responses, like a {@code @ResponseFilter} method. The response filters of a route
     * and of its groups run after the route and before the response filters of the server filters:
     * the filters of the route first, then those of the inner groups, then those of the outer group,
     * each in the order they are declared.
     *
     * @param filter The filter
     * @return This
     */
    S after(RouteResponseFilter filter);

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
     * Filter the responses asynchronously: the filter chain continues when the stage completes.
     *
     * @param filter The filter
     * @return This
     */
    S afterAsync(AsyncRouteResponseFilter filter);

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

    /**
     * Filter the responses with a filter that can replace the response, like a
     * {@code @ResponseFilter} method returning a response: the response it returns is the response
     * the response filters after it and the client see, and {@code null} continues with the
     * response it was given. Otherwise the same as {@link #after(RouteResponseFilter)}, which
     * takes a filter that returns nothing, so that its lambdas are never ambiguous.
     *
     * @param filter The filter, which can change the response in place or replace it
     * @return This
     * @see ReplacingRouteResponseFilter
     */
    S afterReplacing(ReplacingRouteResponseFilter filter);

    /**
     * Filter the responses on the named executor with a filter that can replace the response.
     *
     * @param executorName The name of the executor, e.g. {@code TaskExecutors.BLOCKING}
     * @param filter       The filter, which can change the response in place or replace it
     * @return This
     * @see #after(String, RouteResponseFilter)
     * @see ReplacingRouteResponseFilter
     */
    S afterReplacing(String executorName, ReplacingRouteResponseFilter filter);

    /**
     * Filter the responses asynchronously with a filter that can replace the response, like a
     * {@code @ResponseFilter} method returning a {@code CompletionStage} of a response: the filter
     * chain continues with the response the stage completes with, or with the response the filter
     * was given if it completes with {@code null}.
     *
     * @param filter The filter, which can change the response in place or replace it
     * @return This
     * @see #afterAsync(AsyncRouteResponseFilter)
     * @see AsyncReplacingRouteResponseFilter
     */
    S afterReplacingAsync(AsyncReplacingRouteResponseFilter filter);

    /**
     * Filter the responses with a filter that can replace the response and changes the propagated
     * context of the response filters after it.
     *
     * @param filter The filter, which can change the response in place or replace it
     * @return This
     * @see #afterReplacing(ReplacingRouteResponseFilter)
     * @see ContextReplacingRouteResponseFilter
     */
    S afterReplacing(ContextReplacingRouteResponseFilter filter);

    /**
     * Filter the responses on the named executor with a filter that can replace the response and
     * changes the propagated context.
     *
     * @param executorName The name of the executor, e.g. {@code TaskExecutors.BLOCKING}
     * @param filter       The filter, which can change the response in place or replace it
     * @return This
     * @see #afterReplacing(String, ReplacingRouteResponseFilter)
     * @see ContextReplacingRouteResponseFilter
     */
    S afterReplacing(String executorName, ContextReplacingRouteResponseFilter filter);

    /**
     * Filter the responses asynchronously with a filter that can replace the response and changes
     * the propagated context until its stage completes.
     *
     * @param filter The filter, which can change the response in place or replace it
     * @return This
     * @see #afterReplacingAsync(AsyncReplacingRouteResponseFilter)
     * @see AsyncContextReplacingRouteResponseFilter
     */
    S afterReplacingAsync(AsyncContextReplacingRouteResponseFilter filter);
}
