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

import io.micronaut.http.MediaType;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * What an error or a status route answers in its scope: two routes of a scope with the same key
 * are duplicates, as the lookup cannot tell them apart. The lookup also picks a route by the
 * {@code Accept} header of the request and by the conditions of the route, so routes producing
 * other media types are not duplicates, nor is a route producing its media types only and one
 * that also answers any other accepted type, e.g. a default route without {@code @Produces}, and
 * a route with conditions has no key.
 *
 * @param handled The exception type of an error route, or the status code of a status route
 * @param originatingType The class the route is local to, or {@code null} for a global route
 * @param produces The media types the route produces
 * @param producesAll Whether the route answers any accepted media type, see {@link RouteInfo#producesAll()}
 */
record ErrorRouteKey(Object handled, @Nullable Class<?> originatingType, List<MediaType> produces, boolean producesAll) {

    /**
     * @param route The error route
     * @return The key of the route, or {@code null} for a route with conditions
     */
    static @Nullable ErrorRouteKey of(ErrorRouteInfo<?, ?> route) {
        if (hasConditions(route)) {
            return null;
        }
        return new ErrorRouteKey(route.exceptionType(), route.originatingType(), route.getProduces(), route.producesAll());
    }

    /**
     * @param route The status route
     * @return The key of the route, or {@code null} for a route with conditions
     */
    static @Nullable ErrorRouteKey of(StatusRouteInfo<?, ?> route) {
        if (hasConditions(route)) {
            return null;
        }
        return new ErrorRouteKey(route.statusCode(), route.originatingType(), route.getProduces(), route.producesAll());
    }

    private static boolean hasConditions(RouteInfo<?> route) {
        // a route implemented elsewhere may match any request: not compared
        return !(route instanceof DefaultRequestMatcher<?, ?> matcher) || matcher.hasConditions();
    }
}
