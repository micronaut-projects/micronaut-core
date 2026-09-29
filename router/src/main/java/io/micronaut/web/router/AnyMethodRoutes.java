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

import java.util.ArrayList;
import java.util.List;

/**
 * The routes of {@code HttpRouteBuilder.any(...)}: a route per standard HTTP method, and a route
 * under the {@link #CUSTOM_METHODS} name that matches every custom HTTP method. Among matches
 * that are otherwise equally close, a route of a specific method, including an implicit
 * {@code HEAD} route, is preferred to a route of any method.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class AnyMethodRoutes {

    /**
     * The HTTP method name of the route of any method that matches the custom HTTP methods.
     */
    public static final String CUSTOM_METHODS = "*";

    private AnyMethodRoutes() {
    }

    /**
     * @param route A route
     * @return Whether it is a route of any method
     */
    static boolean isAnyMethod(UriRouteInfo<?, ?> route) {
        return route instanceof DefaultUrlRouteInfo<?, ?> defaultRoute && defaultRoute.anyMethod;
    }

    /**
     * Drops the routes of any method from otherwise indistinguishable matches, provided a route
     * of a specific method remains.
     *
     * @param matches The ambiguous matches, never empty
     * @param <T>     The target type
     * @param <R>     The result type
     * @return The matches of a specific method, or {@code matches} unchanged if the tie-break does not apply
     */
    static <T, R> List<UriRouteMatch<T, R>> preferSpecificMethod(List<UriRouteMatch<T, R>> matches) {
        int anyCount = 0;
        for (UriRouteMatch<T, R> match : matches) {
            if (isAnyMethod(match.getRouteInfo())) {
                anyCount++;
            }
        }
        if (anyCount == 0 || anyCount == matches.size()) {
            return matches;
        }
        List<UriRouteMatch<T, R>> specific = new ArrayList<>(matches.size() - anyCount);
        for (UriRouteMatch<T, R> match : matches) {
            if (!isAnyMethod(match.getRouteInfo())) {
                specific.add(match);
            }
        }
        return specific;
    }
}
