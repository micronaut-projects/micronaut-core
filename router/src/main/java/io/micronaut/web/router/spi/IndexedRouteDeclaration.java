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
package io.micronaut.web.router.spi;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.http.uri.UriTemplateMatcher;
import io.micronaut.web.router.builder.RouteDeclaration;
import org.jspecify.annotations.Nullable;

/**
 * A {@link RouteDeclaration} generated at compile time, with the keys the router indexes and
 * orders routes by: the router registers the bound route with them, without parsing its template,
 * and builds it the first time it is used. A contract for the authors of annotation processors
 * that generate declarations, typically as the constants of an enum; applications declare routes
 * with {@link RouteDeclaration}.
 *
 * <pre>{@code
 * public enum PetRoutes implements IndexedRouteDeclaration {
 *     FIND(HttpMethod.GET, "/pets/{id}", "/pets/", 6, 1);
 *     ...
 * }
 * }</pre>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface IndexedRouteDeclaration extends RouteDeclaration {

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
     * The number of path variables constrained by a regular expression, the third key of the
     * order of routes. The default computes it from the template; a generated declaration may
     * return the value computed at compile time.
     *
     * @return The number of path variables with a regular expression; the same as
     * {@link UriTemplateMatcher#getPatternVariableCount()}
     */
    default int patternVariableCount() {
        String uriTemplate = uriTemplate();
        if (uriTemplate.indexOf(':') < 0) {
            // no variable has a modifier
            return 0;
        }
        return new UriTemplateMatcher(new UriMatchTemplate(uriTemplate).getTemplateString()).getPatternVariableCount();
    }

    /**
     * The URL parser generated for the enum this declaration is a constant of, if any: the
     * ordinal it answers selects the route bound to the constant with that ordinal.
     *
     * @return The matcher, or {@code null}
     */
    default @Nullable CompiledRouteMatcher matcher() {
        return null;
    }

    /**
     * A declaration with the keys computed from the template, as the router computes them for a
     * route built at runtime.
     *
     * @param httpMethod  The HTTP method
     * @param uriTemplate The URI template
     * @return The declaration
     */
    static IndexedRouteDeclaration of(HttpMethod httpMethod, String uriTemplate) {
        return of(httpMethod, httpMethod.name(), uriTemplate);
    }

    /**
     * A declaration of a method by its name, including a custom HTTP method, with the keys
     * computed from the template. A standard name maps to its {@link HttpMethod}; any other name
     * to {@link HttpMethod#CUSTOM} with that name.
     *
     * @param httpMethodName The name of the HTTP method
     * @param uriTemplate    The URI template
     * @return The declaration
     */
    static IndexedRouteDeclaration of(String httpMethodName, String uriTemplate) {
        HttpMethod httpMethod = HttpMethod.parse(httpMethodName);
        // a standard method by its canonical name, a custom one by the given name
        return of(httpMethod, httpMethod == HttpMethod.CUSTOM ? httpMethodName : httpMethod.name(), uriTemplate);
    }

    private static IndexedRouteDeclaration of(HttpMethod httpMethod, String httpMethodName, String uriTemplate) {
        UriTemplateMatcher matcher = new UriTemplateMatcher(new UriMatchTemplate(uriTemplate).getTemplateString());
        return new DefaultRouteDeclaration(httpMethod, httpMethodName, uriTemplate, matcher.getRequiredPrefix(), matcher.getRawLength(), matcher.getPathVariableCount(), matcher.getPatternVariableCount());
    }
}
