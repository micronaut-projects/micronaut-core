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
 * Tie-break used as the very last step of route resolution, once every other discriminator
 * (media types, versioning, URI specificity, ...) has been applied and more than one route still
 * matches the request.
 *
 * <p>A {@code @Get} mapping also registers an implicit {@code HEAD} route so that a bare {@code GET}
 * handler answers {@code HEAD} requests too. When the same controller additionally declares an
 * explicit {@code @Head} route for the same URI, both routes match a {@code HEAD} request with equal
 * specificity and the request would otherwise be rejected with a
 * {@link io.micronaut.web.router.exceptions.DuplicateRouteException} (surfaced as a 400 response).
 * The user's explicit declaration is the more specific intent, so it wins.</p>
 *
 * <p>Applying this only at the point of failure — rather than suppressing the implicit route when it
 * is built — keeps the implicit route available to every earlier discriminator. Two routes for the
 * same URI that differ by {@code produces} or by {@code @Version} are not ambiguous at all, and both
 * remain individually reachable.</p>
 *
 * @author Graeme Rocher
 * @since 5.2.0
 */
@Internal
final class ImplicitHeadRoutes {

    private ImplicitHeadRoutes() {
    }

    /**
     * Drops implicit {@code HEAD} routes from a set of otherwise indistinguishable matches, provided
     * at least one explicitly declared route remains.
     *
     * @param matches The ambiguous matches, never empty
     * @param <T>     The target type
     * @param <R>     The result type
     * @return The explicitly declared matches, or {@code matches} unchanged if the tie-break does not apply
     */
    static <T, R> List<UriRouteMatch<T, R>> preferExplicit(List<UriRouteMatch<T, R>> matches) {
        int implicitCount = 0;
        for (UriRouteMatch<T, R> match : matches) {
            if (match.getRouteInfo().isImplicitHead()) {
                implicitCount++;
            }
        }
        if (implicitCount == 0 || implicitCount == matches.size()) {
            // nothing implicit to drop, or every candidate is implicit: leave the ambiguity untouched
            return matches;
        }
        List<UriRouteMatch<T, R>> explicit = new ArrayList<>(matches.size() - implicitCount);
        for (UriRouteMatch<T, R> match : matches) {
            if (!match.getRouteInfo().isImplicitHead()) {
                explicit.add(match);
            }
        }
        return explicit;
    }
}
