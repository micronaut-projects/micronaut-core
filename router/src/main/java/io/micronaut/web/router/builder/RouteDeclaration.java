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
import io.micronaut.http.HttpMethod;
import io.micronaut.web.router.RouteArguments;

import java.util.Objects;

/**
 * The declaration of a route: its HTTP method and URI template, to bind a handler function to with
 * {@link HttpRouteBuilder#handle(RouteDeclaration, RequestHandler)} and the other {@code handle}
 * methods taking a declaration. It separates the shape of a route from its implementation: a
 * declaration without a handler is not a route.
 *
 * <pre>{@code
 * static final RouteDeclaration FIND = RouteDeclaration.of(HttpMethod.GET, "/pets/{id}");
 *
 * routes.handle(FIND, (request, pathVariables) -> HttpResponse.ok(pets.find(pathVariables.getLong("id"))));
 * }</pre>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface RouteDeclaration {

    /**
     * @return The HTTP method
     */
    HttpMethod httpMethod();

    /**
     * The name of the HTTP method the route answers: {@link HttpMethod#name()} for a standard
     * method, and the actual name, e.g. {@code PROPFIND}, for {@link HttpMethod#CUSTOM}.
     *
     * @return The name of the HTTP method
     */
    default String httpMethodName() {
        return httpMethod().name();
    }

    /**
     * @return The URI template, e.g. {@code /pets/{id}}
     */
    String uriTemplate();

    /**
     * Declare a route in code.
     *
     * @param httpMethod  The HTTP method, not {@link HttpMethod#CUSTOM}: a route of a custom
     *                    method is declared by its name, see {@link #of(String, String)}
     * @param uriTemplate The URI template
     * @return The declaration
     * @throws IllegalArgumentException if the method is {@link HttpMethod#CUSTOM}
     */
    static RouteDeclaration of(HttpMethod httpMethod, String uriTemplate) {
        RouteArguments.standardMethod(httpMethod, "RouteDeclaration.of(\"PROPFIND\", uriTemplate)");
        Objects.requireNonNull(uriTemplate, "uriTemplate");
        return new DefaultRouteDeclaration(httpMethod, httpMethod.name(), uriTemplate);
    }

    /**
     * Declare a route of a method by its name, including a custom HTTP method such as
     * {@code PROPFIND}. A standard name maps to its {@link HttpMethod}; any other name to
     * {@link HttpMethod#CUSTOM} with that name.
     *
     * @param httpMethodName The name of the HTTP method, a token
     * @param uriTemplate    The URI template
     * @return The declaration
     * @throws IllegalArgumentException if the name is empty or not a token, e.g. blank
     */
    static RouteDeclaration of(String httpMethodName, String uriTemplate) {
        RouteArguments.httpMethodName(httpMethodName);
        Objects.requireNonNull(uriTemplate, "uriTemplate");
        HttpMethod httpMethod = HttpMethod.parse(httpMethodName);
        // a standard method by its canonical name, a custom one by the given name
        return new DefaultRouteDeclaration(httpMethod, httpMethod == HttpMethod.CUSTOM ? httpMethodName : httpMethod.name(), uriTemplate);
    }
}
