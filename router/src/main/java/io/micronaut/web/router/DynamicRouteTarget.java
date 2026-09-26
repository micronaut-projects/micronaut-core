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
package io.micronaut.web.router;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.filter.GenericHttpFilter;
import io.micronaut.http.uri.UriMatchInfo;
import io.micronaut.web.router.builder.HandlerMethod;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * The target of a handler route that is resolved when a request matches the route, instead of
 * being the route itself: the {@link UriRouteSet} gives a match of such a route to its target,
 * which answers the matches to use in its place, e.g. the matches of other routes for the rest
 * of the path.
 *
 * <p>This is the extension point of the router for routes resolved at request time. No route of
 * the router has such a target: a {@link HandlerMethod} whose {@link HandlerMethod#getTarget()}
 * implements this interface enables it. A match that a target answers may carry a
 * {@link ResolvedMatchInfo}: the router then adds its filters before the filters of the route,
 * binds its target to the path variables of a handler, and consults its error scopes after the
 * ones of the route.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
interface DynamicRouteTarget {

    /**
     * @param route A route
     * @return The dynamic target of the route, or {@code null} for an ordinary route
     */
    static @Nullable DynamicRouteTarget of(UriRouteInfo<?, ?> route) {
        // a handler method only: the target of a bean method route is the bean, created when asked for
        if (route instanceof DefaultUrlRouteInfo<?, ?> defaultRoute
            && defaultRoute.getTargetMethod() instanceof HandlerMethod<?> handler
            && handler.getTarget() instanceof DynamicRouteTarget target) {
            return target;
        }
        return null;
    }

    /**
     * The closest match to use instead of the closest match of the route, see
     * {@link Router#findClosest(HttpRequest)}.
     *
     * @param request The request
     * @param match   The match of the route
     * @param <T>     The target type
     * @param <R>     The result type
     * @return The match, or {@code null} if none
     */
    <T, R> @Nullable UriRouteMatch<T, R> findClosest(HttpRequest<?> request, UriRouteMatch<T, R> match);

    /**
     * The closest matches to use instead of a closest match of the route, see
     * {@link Router#findAllClosest(HttpRequest)}.
     *
     * @param request The request
     * @param match   The match of the route
     * @param filter  The filter of the candidates, applied to the resolved matches before their
     *                ambiguity is resolved, or {@code null}
     * @param <T>     The target type
     * @param <R>     The result type
     * @return The matches
     */
    <T, R> List<UriRouteMatch<T, R>> findAllClosest(HttpRequest<?> request, UriRouteMatch<T, R> match,
                                                    @Nullable Predicate<UriRouteMatch<T, R>> filter);

    /**
     * The matches of any method to use instead of a match of the route, see
     * {@link Router#findAny(HttpRequest)}, e.g. to find the allowed methods of a path. The route
     * set asks the most specific dynamic route of the request only.
     *
     * @param request The request
     * @param match   The match of the route
     * @param <T>     The target type
     * @param <R>     The result type
     * @return The matches
     */
    <T, R> List<UriRouteMatch<T, R>> findAny(HttpRequest<?> request, UriRouteMatch<T, R> match);

    /**
     * The match info of a match a dynamic target answered.
     */
    interface ResolvedMatchInfo extends UriMatchInfo {

        /**
         * @return The resolved target, given to the path variables of a handler, or {@code null}
         */
        @Nullable Object target();

        /**
         * @return The filters that run before the filters of the matched route
         */
        List<GenericHttpFilter> filters();

        /**
         * @return The groups whose error and status routes apply after the ones of the matched route
         */
        List<RouteAssembly.RouteGroup> errorScopes();
    }
}
