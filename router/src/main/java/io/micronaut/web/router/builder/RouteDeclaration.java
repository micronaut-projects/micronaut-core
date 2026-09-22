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
import io.micronaut.http.uri.RouteTemplate;
import io.micronaut.web.router.spi.IndexedRouteDeclaration;

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
 * <p>Annotation processors generate declarations as {@link IndexedRouteDeclaration}s, with the
 * keys the router indexes routes by computed at compile time, or as
 * {@link io.micronaut.web.router.spi.PlannedRouteDeclaration}s bound to the slots of a generated
 * {@link io.micronaut.web.router.spi.RoutePlan}.</p>
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
     * @return The expression of the URI template, e.g. {@code /pets/{id}}. For a template of an
     * engine other than the Micronaut one, the expression in the language of that engine, see
     * {@link #template()}
     */
    String uriTemplate();

    /**
     * The template of the route with the engine of its language. The default is the
     * {@link #uriTemplate() expression} as a {@link RouteTemplate#MICRONAUT Micronaut} template;
     * a declaration in another language overrides it and keeps {@link #uriTemplate()} as its
     * expression.
     *
     * @return The template
     */
    default RouteTemplate template() {
        return RouteTemplate.micronaut(uriTemplate());
    }

    /**
     * Declare a route in code. The route is registered like a generated declaration, built the
     * first time the router uses it.
     *
     * @param httpMethod  The HTTP method
     * @param uriTemplate The URI template
     * @return The declaration
     */
    static RouteDeclaration of(HttpMethod httpMethod, String uriTemplate) {
        return IndexedRouteDeclaration.of(httpMethod, uriTemplate);
    }

    /**
     * Declare a route of a method by its name, including a custom HTTP method such as
     * {@code PROPFIND}. A standard name maps to its {@link HttpMethod}; any other name to
     * {@link HttpMethod#CUSTOM} with that name.
     *
     * @param httpMethodName The name of the HTTP method
     * @param uriTemplate    The URI template
     * @return The declaration
     */
    static RouteDeclaration of(String httpMethodName, String uriTemplate) {
        return IndexedRouteDeclaration.of(httpMethodName, uriTemplate);
    }

    /**
     * Declare a route in code with a template of any registered
     * {@link io.micronaut.http.uri.spi.RouteTemplateEngine engine}. The engine is resolved when
     * the routes are assembled; the application fails to start if it is not registered.
     *
     * <pre>{@code
     * RouteDeclaration.of(HttpMethod.GET, RouteTemplate.of("jaxrs", "/items/{id: [0-9]+}"));
     * }</pre>
     *
     * @param httpMethod The HTTP method
     * @param template   The template
     * @return The declaration
     */
    static RouteDeclaration of(HttpMethod httpMethod, RouteTemplate template) {
        return IndexedRouteDeclaration.of(httpMethod, template);
    }

    /**
     * Declare a route of a method by its name, including a custom HTTP method such as
     * {@code PROPFIND}, with a template of any registered
     * {@link io.micronaut.http.uri.spi.RouteTemplateEngine engine}. A standard name maps to its
     * {@link HttpMethod}; any other name to {@link HttpMethod#CUSTOM} with that name.
     *
     * @param httpMethodName The name of the HTTP method
     * @param template       The template
     * @return The declaration
     * @since 5.3.0
     */
    static RouteDeclaration of(String httpMethodName, RouteTemplate template) {
        return IndexedRouteDeclaration.of(httpMethodName, template);
    }
}
