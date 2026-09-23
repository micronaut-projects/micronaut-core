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
import io.micronaut.http.HttpMethod;
import io.micronaut.http.filter.FilterPatternStyle;

/**
 * A server filter declared with {@link HttpRouteBuilder#filter(String...)}: the functional form of
 * a {@code @ServerFilter} bean, whose {@code before} and {@code after} filters are its
 * {@code @RequestFilter} and {@code @ResponseFilter} methods.
 *
 * <pre>{@code
 * routes.filter("/**").order(100).before((request, propagatedContext) -> {
 *     propagatedContext.add(new MdcPropagationContext(Map.of("path", request.getPath())));
 *     return null;
 * });
 * }</pre>
 *
 * <p>Like a {@code @ServerFilter} bean, it filters every request whose path matches one of its
 * patterns, and whose method is one of its {@link #methods}, if it has any: the requests to
 * controllers, to handler routes, to static resources, and the requests no route matches, e.g. a
 * {@code 404} or a {@code 405}. It is global wherever it is declared: in any {@link HttpRoutes}
 * bean, and in a {@link HttpRouteGroup}, whose prefix and filters do not apply to it.</p>
 *
 * <p>It runs with the filter beans, ordered by {@link #order}, like a filter bean ordered by
 * {@code @Order}: a lower order runs first on the request and last on the response. Its default
 * order is {@code 0}, the order of a {@code @ServerFilter} bean without an order. Like between
 * filter beans, the order is the only guarantee: set it to run before or after a filter bean. The
 * filters of the route groups and of the route run after
 * every server filter, closest to the route. The request filters of one server filter run in the
 * order they are declared, and so do its response filters; they also filter the response a request
 * filter of the same server filter answered with.</p>
 *
 * <p>Like the request filters of a {@code @ServerFilter} bean annotated {@code @PreMatching}, its
 * request filters can run before the route is matched, and change the request that is matched,
 * see {@link #preMatching()}.</p>
 *
 * <p>Like the patterns of a {@code @ServerFilter}, the patterns are under
 * {@code micronaut.server.context-path}, unless they start with it or
 * {@link #appendContextPath(boolean)} says otherwise. Each call of
 * {@link HttpRouteBuilder#filter(String...)} declares a new server filter: declaring the same one
 * twice filters the requests twice. A route table built at runtime cannot declare server filters.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public sealed interface ServerFilterSpec extends RouteFilterSpec<ServerFilterSpec> permits DefaultServerFilterSpec {

    /**
     * Filter the requests of these methods only, like {@code @ServerFilter(methods = ...)}.
     *
     * @param methods The methods
     * @return This
     */
    ServerFilterSpec methods(HttpMethod... methods);

    /**
     * The order of the filter among the server filters, like {@code @Order} on a filter bean:
     * a lower order runs first on the request.
     *
     * @param order The order, {@code 0} by default
     * @return This
     */
    ServerFilterSpec order(int order);

    /**
     * The style of the patterns, like {@code @ServerFilter(patternStyle = ...)}.
     *
     * @param patternStyle The style, {@link FilterPatternStyle#ANT} by default
     * @return This
     */
    ServerFilterSpec patternStyle(FilterPatternStyle patternStyle);

    /**
     * Whether the patterns are under {@code micronaut.server.context-path}, like
     * {@code @ServerFilter(appendContextPath = ...)}.
     *
     * @param appendContextPath {@code true} by default
     * @return This
     */
    ServerFilterSpec appendContextPath(boolean appendContextPath);

    /**
     * Run the request filters before the route is matched, like {@code @RequestFilter} methods
     * annotated {@code @PreMatching}: the route is matched with the request they continue with,
     * e.g. with the URI they changed in place or the method of the request they returned, see
     * {@link RouteRequestFilter}. They run with the pre-matching filter beans, ordered by
     * {@link #order(int)}, before the filters that run once the route is matched, and they filter
     * the requests of the patterns and methods, as they were received, including those no route
     * matches. The patterns and methods select the filter before the request filters run.
     *
     * <pre>{@code
     * routes.filter("/legacy/**").preMatching().before(request -> {
     *     request.uri(URI.create(request.getPath().replaceFirst("/legacy", "/api")));
     *     return null;
     * });
     * }</pre>
     *
     * <p>Its response filters filter every response: the response a request filter answered
     * with before the route was matched, like {@code @ResponseFilter} methods annotated
     * {@code @PreMatching}, and otherwise the response of the route, like {@code @ResponseFilter}
     * methods, ordered by {@link #order(int)} with the filter beans. They filter each response
     * once.</p>
     *
     * @return This
     */
    ServerFilterSpec preMatching();
}
