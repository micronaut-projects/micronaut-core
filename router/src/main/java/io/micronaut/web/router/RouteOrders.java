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
 * The last tie-break of route resolution, after {@link ImplicitHeadRoutes#preferExplicit}: the
 * routes with the lowest {@link UriRouteInfo#getOrder() order} among the routes that are still
 * equally good for the request. The order never overrides the specificity of the templates or
 * the media types, which decide first; routes left with the same order stay ambiguous.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class RouteOrders {

    private RouteOrders() {
    }

    /**
     * @param matches The ambiguous matches, more than one
     * @param <T>     The target type
     * @param <R>     The result type
     * @return The matches with the lowest order, or {@code matches} unchanged if they all have the same order
     */
    static <T, R> List<UriRouteMatch<T, R>> preferLowest(List<UriRouteMatch<T, R>> matches) {
        int size = matches.size();
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        int[] orders = new int[size];
        for (int i = 0; i < size; i++) {
            int order = matches.get(i).getRouteInfo().getOrder();
            orders[i] = order;
            min = Math.min(min, order);
            max = Math.max(max, order);
        }
        if (min == max) {
            return matches;
        }
        List<UriRouteMatch<T, R>> lowest = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            if (orders[i] == min) {
                lowest.add(matches.get(i));
            }
        }
        return lowest;
    }
}
