/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.web.router.shortcircuit;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;

/**
 * This API can be passed to a {@link io.micronaut.web.router.Router} to make it list all
 * available routes explicitly. The implementation of this class can then build an optimized
 * router instead of relying on the "legacy" routing exposed by the
 * {@link io.micronaut.web.router.Router}.
 * <p>
 * Routes are associated with {@link MatchRule}s. The {@link MatchRule} decides whether the route
 * should be matched. When two {@link MatchRule}s for different routes match the same request, it
 * is not possible to use this optimized routing, and we have to fall back on legacy routing.
 * <p>
 * Some routes may have matching logic that cannot be expressed as a {@link MatchRule}. These
 * routes must still be added if they may potentially conflict with other routes. They can be
 * marked for "legacy" (non-optimized) routing.
 *
 * @param <R> The type of route endpoint to collect. This is set to
 *            {@link io.micronaut.web.router.UriRouteInfo} by
 *            {@link io.micronaut.web.router.Router}.
 * @author Jonas Konrad
 * @since 4.3.0
 */
@Internal
@Experimental
public interface ShortCircuitRouterBuilder<R> {
    /**
     * Add a route that can be fully matched by the given rule.
     *
     * @param rule  The rule that matches this route
     * @param match The matched route
     */
    void addRoute(MatchRule rule, R match);

    /**
     * If the given rule is matched, we <i>must</i> revert to legacy routing, even when another
     * route matches the same request.
     *
     * @param rule The rule to match
     */
    void addLegacyRoute(MatchRule rule);

    /**
     * This method may set a flag that the route list is not exhaustive. If no routes match, we
     * should fall back to legacy routing.
     */
    void addLegacyFallbackRouting();
}
