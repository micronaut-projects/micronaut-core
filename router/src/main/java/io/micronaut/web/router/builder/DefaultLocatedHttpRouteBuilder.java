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
import io.micronaut.http.uri.RouteTemplate;
import io.micronaut.web.router.RouteAssembly;
import io.micronaut.web.router.RouteTable;

import java.util.Objects;
import java.util.function.Function;

/**
 * The builder of the route table of located targets of a type: the handlers that receive the
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
        super(assembly, null, null, null);
        this.targetType = Objects.requireNonNull(targetType, "targetType");
    }

    @Override
    public Argument<T> targetType() {
        return targetType;
    }

    /**
     * Close the builder once the routes of the table were declared on it: a route declared on it
     * later fails with an {@link IllegalStateException}, instead of being dropped.
     */
    public void close() {
        closeBuilder();
    }

    @Override
    public HttpRouteSpec handle(HttpMethod method, String uri, LocatedRequestHandler<T> handler) {
        Objects.requireNonNull(handler, "handler");
        return handle(method, uri, (RequestHandler) (request, pathVariables) -> handler.handle(request, pathVariables, target(pathVariables)));
    }

    @Override
    public <B> HttpRouteSpec handle(HttpMethod method, String uri, Argument<B> bodyType, LocatedBodyRequestHandler<T, B> handler) {
        Objects.requireNonNull(handler, "handler");
        return handle(method, uri, bodyType, (BodyRequestHandler<B>) (request, pathVariables, body) ->
            handler.handle(request, pathVariables, target(pathVariables), body));
    }

    @Override
    public HttpRouteSpec handleForm(HttpMethod method, String uri, LocatedFormRequestHandler<T> handler) {
        Objects.requireNonNull(handler, "handler");
        return handleForm(method, uri, (FormRequestHandler) (request, pathVariables, form) ->
            handler.handle(request, pathVariables, target(pathVariables), form));
    }

    @Override
    public HttpRouteSpec handleAsync(HttpMethod method, String uri, LocatedAsyncRequestHandler<T> handler) {
        Objects.requireNonNull(handler, "handler");
        return handleAsync(method, uri, (AsyncRequestHandler) (request, pathVariables) ->
            handler.handle(request, pathVariables, target(pathVariables)));
    }

    @Override
    public HttpRouteSpec handle(RouteDeclaration route, LocatedRequestHandler<T> handler) {
        Objects.requireNonNull(handler, "handler");
        return handle(route, (RequestHandler) (request, pathVariables) -> handler.handle(request, pathVariables, target(pathVariables)));
    }

    @Override
    public <B> HttpRouteSpec handle(RouteDeclaration route, Argument<B> bodyType, LocatedBodyRequestHandler<T, B> handler) {
        Objects.requireNonNull(handler, "handler");
        return handle(route, bodyType, (BodyRequestHandler<B>) (request, pathVariables, body) ->
            handler.handle(request, pathVariables, target(pathVariables), body));
    }

    @Override
    public HttpRouteSpec handleForm(RouteDeclaration route, LocatedFormRequestHandler<T> handler) {
        Objects.requireNonNull(handler, "handler");
        return handleForm(route, (FormRequestHandler) (request, pathVariables, form) ->
            handler.handle(request, pathVariables, target(pathVariables), form));
    }

    @Override
    public HttpRouteSpec handleAsync(RouteDeclaration route, LocatedAsyncRequestHandler<T> handler) {
        Objects.requireNonNull(handler, "handler");
        return handleAsync(route, (AsyncRequestHandler) (request, pathVariables) ->
            handler.handle(request, pathVariables, target(pathVariables)));
    }

    @Override
    public <U> void locate(String prefixUri, LocatedLocatorHandler<T, ? extends U> locator, Function<? super U, RouteTable> tables) {
        Objects.requireNonNull(locator, "locator");
        LocatorHandler<U> untyped = (request, pathVariables) -> locator.locate(request, pathVariables, target(pathVariables));
        locate(prefixUri, untyped, tables);
    }

    @Override
    public <U> void locateAsync(String prefixUri, LocatedAsyncLocatorHandler<T, ? extends U> locator, Function<? super U, RouteTable> tables) {
        Objects.requireNonNull(locator, "locator");
        AsyncLocatorHandler<U> untyped = (request, pathVariables) -> locator.locate(request, pathVariables, target(pathVariables));
        locateAsync(prefixUri, untyped, tables);
    }

    @Override
    public <U> void locate(RouteTemplate prefix, LocatedLocatorHandler<T, ? extends U> locator, Function<? super U, RouteTable> tables) {
        Objects.requireNonNull(locator, "locator");
        LocatorHandler<U> untyped = (request, pathVariables) -> locator.locate(request, pathVariables, target(pathVariables));
        locate(prefix, untyped, tables);
    }

    @Override
    public <U> void locateAsync(RouteTemplate prefix, LocatedAsyncLocatorHandler<T, ? extends U> locator, Function<? super U, RouteTable> tables) {
        Objects.requireNonNull(locator, "locator");
        AsyncLocatorHandler<U> untyped = (request, pathVariables) -> locator.locate(request, pathVariables, target(pathVariables));
        locateAsync(prefix, untyped, tables);
    }

    /**
     * The located target of a route of the table, of the type of the table.
     *
     * @param pathVariables The path variables of the route
     * @return The target
     */
    @SuppressWarnings("unchecked")
    private T target(PathVariables pathVariables) {
        Object target = pathVariables.locatedTarget();
        if (!targetType.getWrapperType().isInstance(target)) {
            throw new IllegalStateException("The located target is not a " + targetType.getTypeName() + ": " + target);
        }
        return (T) target;
    }
}
