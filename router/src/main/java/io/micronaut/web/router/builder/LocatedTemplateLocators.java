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

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.uri.RouteTemplate;

import java.util.function.Function;

/**
 * The locators of a {@link LocatedHttpRouteScope} whose prefix is already parsed, shared by the
 * builder and the groups of the routes of located targets.
 *
 * @param <T> The type of the located target
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
sealed interface LocatedTemplateLocators<T> extends LocatedHttpRouteScope<T> permits DefaultLocatedHttpRouteBuilder, DefaultLocatedHttpRouteGroup {

    /**
     * @return Reads the located target of these routes
     */
    LocatedTarget<T> locatedTarget();

    @Override
    default <U> void locate(RouteTemplate prefixTemplate, LocatedLocatorHandler<T, ? extends U> locator,
                            Function<? super U, ? extends LocatedRoutes<?>> routesOf) {
        locate(prefixTemplate, locatedTarget().<U>locator(locator), routesOf);
    }

    @Override
    default <U> void locateOn(String executorName, RouteTemplate prefixTemplate, LocatedLocatorHandler<T, ? extends U> locator,
                              Function<? super U, ? extends LocatedRoutes<?>> routesOf) {
        locateOn(executorName, prefixTemplate, locatedTarget().<U>locator(locator), routesOf);
    }

    @Override
    default <U> void locateAsync(RouteTemplate prefixTemplate, LocatedAsyncLocatorHandler<T, ? extends U> locator,
                                 Function<? super U, ? extends LocatedRoutes<?>> routesOf) {
        locateAsync(prefixTemplate, locatedTarget().<U>asyncLocator(locator), routesOf);
    }
}
