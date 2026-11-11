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
package io.micronaut.http.server.tck.tests.routing;

import io.micronaut.core.type.Argument;
import io.micronaut.web.router.builder.LocatedHttpRouteBuilder;
import io.micronaut.web.router.builder.LocatedRoutes;

import java.util.function.Consumer;

/**
 * Located routes declared by a callback.
 *
 * @param targetType The type of the located targets
 * @param routes     Declares the routes
 * @param <T>        The type of the located targets
 */
record TckLocatedRoutes<T>(Argument<T> targetType, Consumer<? super LocatedHttpRouteBuilder<T>> routes) implements LocatedRoutes<T> {

    /**
     * @param routes Declares the routes of any target
     * @return The routes
     */
    static TckLocatedRoutes<Object> of(Consumer<? super LocatedHttpRouteBuilder<Object>> routes) {
        return of(Object.class, routes);
    }

    static <T> TckLocatedRoutes<T> of(Class<T> targetType, Consumer<? super LocatedHttpRouteBuilder<T>> routes) {
        return of(Argument.of(targetType), routes);
    }

    static <T> TckLocatedRoutes<T> of(Argument<T> targetType, Consumer<? super LocatedHttpRouteBuilder<T>> routes) {
        return new TckLocatedRoutes<>(targetType, routes);
    }

    @Override
    public void routes(LocatedHttpRouteBuilder<T> builder) {
        routes.accept(builder);
    }
}
