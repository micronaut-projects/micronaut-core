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

import io.micronaut.http.HttpRequest;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Reads the {@link RouteResolution} of a request in the tests of the body conditions: the match,
 * or {@code null} with the pending match given to a consumer.
 */
final class Resolutions {

    private Resolutions() {
    }

    static <T, R> @Nullable UriRouteMatch<T, R> resolve(Router router, HttpRequest<?> request,
                                                        Consumer<? super PendingRouteMatch<T, R>> pending) {
        return resolve(router, request, null, pending);
    }

    static <T, R> @Nullable UriRouteMatch<T, R> resolve(Router router, HttpRequest<?> request,
                                                        @Nullable Predicate<UriRouteMatch<T, R>> filter,
                                                        Consumer<? super PendingRouteMatch<T, R>> pending) {
        RouteResolution<T, R> resolution = router.resolve(request, filter);
        if (resolution instanceof RouteResolution.Pending<T, R> p) {
            pending.accept(p.pending());
            return null;
        }
        return resolution.matchOrNull();
    }

    static <T, R> @Nullable UriRouteMatch<T, R> choose(PendingRouteMatch<T, R> pending, HttpRequest<?> request,
                                                       List<UriRouteMatch<T, R>> matched) {
        RouteResolution<T, R> resolution = pending.choose(request, matched);
        if (resolution instanceof RouteResolution.Pending<T, R>) {
            throw new AssertionError("The chosen route depends on the body again");
        }
        return resolution.matchOrNull();
    }
}
