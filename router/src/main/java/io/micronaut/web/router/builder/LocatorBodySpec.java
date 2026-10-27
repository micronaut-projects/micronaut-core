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
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Function;

/**
 * The terminals of a locator route that reads the body, see {@link LocatorSpec}.
 *
 * @param <B> The type of the body the locator receives
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public sealed interface LocatorBodySpec<B extends @Nullable Object> permits DefaultLocatorSpec.Body {

    /**
     * Locate the target from the body, see {@link HttpRouteScope#locate(String, LocatorHandler, Function)}.
     *
     * @param locator  Locates the target, or answers {@code null} for {@code 404}
     * @param routesOf The routes of a located target
     * @param <T>      The type of the target
     */
    <T> void locate(BodyLocatorHandler<B, ? extends T> locator, Function<? super T, ? extends LocatedRoutes<?>> routesOf);

    /**
     * Locate the target from the body, with one set of routes for every target.
     *
     * @param locator Locates the target, or answers {@code null} for {@code 404}
     * @param routes  The routes of every located target
     * @param <T>     The type of the target
     */
    default <T> void locate(BodyLocatorHandler<B, ? extends T> locator, LocatedRoutes<T> routes) {
        Objects.requireNonNull(routes, "routes");
        locate(locator, target -> routes);
    }

    /**
     * Locate the target from the body asynchronously, see
     * {@link HttpRouteScope#locateAsync(String, AsyncLocatorHandler, Function)}.
     *
     * @param locator  Locates the target later, or completes with {@code null} for {@code 404}
     * @param routesOf The routes of a located target
     * @param <T>      The type of the target
     */
    <T> void locateAsync(AsyncBodyLocatorHandler<B, ? extends T> locator, Function<? super T, ? extends LocatedRoutes<?>> routesOf);

    /**
     * Locate the target from the body asynchronously, with one set of routes for every target.
     *
     * @param locator Locates the target later, or completes with {@code null} for {@code 404}
     * @param routes  The routes of every located target
     * @param <T>     The type of the target
     */
    default <T> void locateAsync(AsyncBodyLocatorHandler<B, ? extends T> locator, LocatedRoutes<T> routes) {
        Objects.requireNonNull(routes, "routes");
        locateAsync(locator, target -> routes);
    }
}
