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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.http.uri.UriTemplateMatcher;

/**
 * The declaration of a route: its HTTP method and URI template, with the keys the router indexes
 * and orders routes by. A handler function is bound to it with
 * {@link RouteBuilder#handle(RouteDeclaration, RequestHandler)} and the other {@code handle}
 * methods taking a declaration.
 *
 * <p>Declarations are meant to be generated at compile time, typically as the constants of an
 * enum, by an annotation processor that reads the web annotations of an application or a
 * framework: the keys are then computed once, and the router registers the route without parsing
 * its template, building it the first time it is used. {@link #of(HttpMethod, String)} declares a
 * route in code.</p>
 *
 * <pre>{@code
 * public enum PetRoutes implements RouteDeclaration {
 *     FIND(HttpMethod.GET, "/pets/{id}", "/pets/", 6, 1);
 *     ...
 * }
 *
 * routes.handle(PetRoutes.FIND, (request, pathVariables) -> HttpResponse.ok(pets.find(pathVariables.getLong("id"))));
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
     * @return The URI template, e.g. {@code /pets/{id}}
     */
    String uriTemplate();

    /**
     * @return The literal every path the route matches starts with, or an empty string; the same
     * as {@link UriTemplateMatcher#getRequiredPrefix()} of the template
     */
    String requiredPathPrefix();

    /**
     * @return The length of the literal parts of the template; the same as
     * {@link UriTemplateMatcher#getRawLength()}
     */
    int rawLength();

    /**
     * @return The number of path variables of the template; the same as
     * {@link UriTemplateMatcher#getPathVariableCount()}
     */
    int pathVariableCount();

    /**
     * Declare a route in code: the keys are computed from the template.
     *
     * @param httpMethod  The HTTP method
     * @param uriTemplate The URI template
     * @return The declaration
     */
    static RouteDeclaration of(HttpMethod httpMethod, String uriTemplate) {
        UriTemplateMatcher matcher = new UriTemplateMatcher(new UriMatchTemplate(uriTemplate).getTemplateString());
        return new DefaultRouteDeclaration(httpMethod, uriTemplate, matcher.getRequiredPrefix(), matcher.getRawLength(), matcher.getPathVariableCount());
    }
}
