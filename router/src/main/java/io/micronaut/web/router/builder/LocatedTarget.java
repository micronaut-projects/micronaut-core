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
import io.micronaut.core.type.Argument;
import io.micronaut.http.PathVariables;

import java.util.Objects;
import java.util.function.Function;

/**
 * Reads the located target of a route of {@link LocatedRoutes} from the path variables of a
 * request, as an instance of the type of the routes, for the handlers and the locators of a
 * {@link LocatedHttpRouteBuilder} and of its groups that receive it. It references the type only,
 * so the handlers that use it do not keep the builder of their routes.
 *
 * @param targetType The type of the located targets
 * @param <T>        The type of the located targets
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
record LocatedTarget<T>(Argument<T> targetType) implements Function<PathVariables, T> {

    LocatedTarget {
        Objects.requireNonNull(targetType, "targetType");
    }

    @Override
    @SuppressWarnings("unchecked")
    public T apply(PathVariables pathVariables) {
        Object target = LocatedRoutes.locatedTarget(pathVariables);
        if (!targetType.getWrapperType().isInstance(target)) {
            throw new IllegalStateException("The located target is not a " + targetType.getTypeName() + ": " + target);
        }
        return (T) target;
    }

    /**
     * @param route A pending route
     * @return The located route, whose located handlers read the target from the path variables
     */
    LocatedHttpRouteSpec<T> route(HttpRouteSpec route) {
        return new DefaultLocatedHttpRouteSpec<>((DefaultHttpRouteSpec) route, this);
    }

    /**
     * @param locator A locator that receives the located target
     * @param <U>     The type of the target it locates
     * @return The locator that reads the located target from the path variables
     */
    <U> LocatorHandler<U> locator(LocatedLocatorHandler<T, ? extends U> locator) {
        Objects.requireNonNull(locator, "locator");
        return (request, pathVariables) -> locator.locate(request, pathVariables, apply(pathVariables));
    }

    /**
     * @param locator An asynchronous locator that receives the located target
     * @param <U>     The type of the target it locates
     * @return The locator that reads the located target from the path variables
     */
    <U> AsyncLocatorHandler<U> asyncLocator(LocatedAsyncLocatorHandler<T, ? extends U> locator) {
        Objects.requireNonNull(locator, "locator");
        return (request, pathVariables) -> locator.locate(request, pathVariables, apply(pathVariables));
    }
}
