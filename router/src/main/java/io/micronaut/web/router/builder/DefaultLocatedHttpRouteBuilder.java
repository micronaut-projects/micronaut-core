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
import io.micronaut.http.HttpMethod;
import io.micronaut.http.PathVariables;
import io.micronaut.web.router.RouteAssembly;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * The builder of the routes of located targets of a type, see {@link LocatedRoutes}: the handlers that receive the
 * target are routed as handlers that read it from their path variables.
 *
 * @param <T> The type of the located target
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class DefaultLocatedHttpRouteBuilder<T> extends AbstractHttpRouteBuilder implements LocatedHttpRouteBuilder<T> {

    private final Argument<T> targetType;

    /**
     * @param assembly   The assembly the routes are added to
     * @param targetType The type of the located targets
     */
    public DefaultLocatedHttpRouteBuilder(RouteAssembly assembly, Argument<T> targetType) {
        this(assembly, targetType, null);
    }

    /**
     * @param assembly   The assembly the routes are added to
     * @param targetType The type of the located targets
     * @param declaredBy The class of the {@link LocatedRoutes} that declares the routes, named by
     *                   the message of a route without a terminal, or {@code null}
     */
    public DefaultLocatedHttpRouteBuilder(RouteAssembly assembly, Argument<T> targetType, @Nullable Class<?> declaredBy) {
        // located routes cannot open a port: no placeholder to resolve
        super(assembly, null, null, null, null);
        this.targetType = Objects.requireNonNull(targetType, "targetType");
        declaredBy(declaredBy);
    }

    @Override
    public Argument<T> targetType() {
        return targetType;
    }

    /**
     * Close the builder once the located routes were declared on it: a route declared on it
     * later fails with an {@link IllegalStateException}, instead of being dropped.
     *
     * @throws IllegalStateException if a route declared on the builder was not ended with a terminal
     */
    public void close() {
        closeBuilder();
        checkEnded();
    }

    /**
     * Close the builder when the declaration of its routes failed: the routes not ended with a
     * terminal are not reported, the failure is.
     */
    public void discard() {
        closeBuilder();
    }

    @Override
    public LocatedHttpRouteSpec<T> route(HttpMethod method, String uri) {
        return located(super.route(method, uri));
    }

    @Override
    public LocatedHttpRouteSpec<T> route(Set<HttpMethod> methods, String uri) {
        return located(super.route(methods, uri));
    }

    @Override
    public LocatedHttpRouteSpec<T> route(String httpMethodName, String uri) {
        return located(super.route(httpMethodName, uri));
    }

    @Override
    public LocatedHttpRouteSpec<T> any(String uri) {
        return located(super.any(uri));
    }

    /**
     * @param route The pending route
     * @return The located route: its located handlers read the target from the path variables
     */
    private LocatedHttpRouteSpec<T> located(HttpRouteSpec route) {
        return new DefaultLocatedHttpRouteSpec<>((DefaultHttpRouteSpec) route, this::target);
    }

    @Override
    public <U> void locate(String prefixUri, LocatedLocatorHandler<T, ? extends U> locator,
                           Function<? super U, ? extends LocatedRoutes<?>> routesOf) {
        Objects.requireNonNull(locator, "locator");
        LocatorHandler<U> untyped = (request, pathVariables) -> locator.locate(request, pathVariables, target(pathVariables));
        locate(prefixUri, untyped, routesOf);
    }

    @Override
    public <U> void locateAsync(String prefixUri, LocatedAsyncLocatorHandler<T, ? extends U> locator,
                                Function<? super U, ? extends LocatedRoutes<?>> routesOf) {
        Objects.requireNonNull(locator, "locator");
        AsyncLocatorHandler<U> untyped = (request, pathVariables) -> locator.locate(request, pathVariables, target(pathVariables));
        locateAsync(prefixUri, untyped, routesOf);
    }

    /**
     * The located target of a located route, of the type of the routes.
     *
     * @param pathVariables The path variables of the route
     * @return The target
     */
    @SuppressWarnings("unchecked")
    private T target(PathVariables pathVariables) {
        Object target = LocatedRoutes.locatedTarget(pathVariables);
        if (!targetType.getWrapperType().isInstance(target)) {
            throw new IllegalStateException("The located target is not a " + targetType.getTypeName() + ": " + target);
        }
        return (T) target;
    }
}
