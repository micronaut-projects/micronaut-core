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
import io.micronaut.http.uri.ParsedRouteTemplate;
import io.micronaut.http.uri.RouteTemplate;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.http.uri.UriTemplateMatcher;
import io.micronaut.http.uri.spi.RouteTemplateEngines;
import io.micronaut.web.router.builder.RouteDeclaration;

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
     * as {@link ParsedRouteTemplate#requiredPrefix()} of the template, for a Micronaut template
     * {@link UriTemplateMatcher#getRequiredPrefix()}
     */
    String requiredPathPrefix();

    /**
     * @return The length of the literal parts of the template; the same as
     * {@link ParsedRouteTemplate#rawLength()}, for a Micronaut template {@link UriTemplateMatcher#getRawLength()}
     */
    int rawLength();

    /**
     * @return The number of path variables of the template; the same as
     * {@link ParsedRouteTemplate#pathVariableCount()}, for a Micronaut template
     * {@link UriTemplateMatcher#getPathVariableCount()}
     */
    int pathVariableCount();

    /**
     * The number of path variables constrained by a regular expression, the third key of the
     * order of routes. The default computes it from the template; a generated declaration may
     * return the value computed at compile time.
     *
     * @return The number of path variables with a regular expression; the same as
     * {@link ParsedRouteTemplate#patternVariableCount()}, for a Micronaut template
     * {@link UriTemplateMatcher#getPatternVariableCount()}
     */
    default int patternVariableCount() {
        RouteTemplate template = template();
        if (!template.isMicronaut()) {
            // the engine of the template counts its variables
            return RouteTemplateEngines.defaults().parse(template).patternVariableCount();
        }
        String uriTemplate = template.expression();
        if (uriTemplate.indexOf(':') < 0) {
            // no variable has a modifier
            return 0;
        }
        return new UriTemplateMatcher(new UriMatchTemplate(uriTemplate).getTemplateString()).getPatternVariableCount();
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

    /**
     * A declaration of a template of any engine, with the keys computed from the template the
     * engine parsed. The engine is resolved, and the template parsed, when the keys are first
     * read, not when the declaration is created.
     *
     * @param httpMethod The HTTP method
     * @param template   The template
     * @return The declaration
     * @since 5.3.0
     */
    static IndexedRouteDeclaration of(HttpMethod httpMethod, RouteTemplate template) {
        return of(httpMethod, httpMethod.name(), template);
    }

    /**
     * A declaration of a method by its name, including a custom HTTP method, with a template of
     * any engine. A standard name maps to its {@link HttpMethod}; any other name to
     * {@link HttpMethod#CUSTOM} with that name.
     *
     * @param httpMethodName The name of the HTTP method
     * @param template       The template
     * @return The declaration
     * @since 5.3.0
     */
    static IndexedRouteDeclaration of(String httpMethodName, RouteTemplate template) {
        HttpMethod httpMethod = HttpMethod.parse(httpMethodName);
        // a standard method by its canonical name, a custom one by the given name
        return of(httpMethod, httpMethod == HttpMethod.CUSTOM ? httpMethodName : httpMethod.name(), template);
    }

    private static IndexedRouteDeclaration of(HttpMethod httpMethod, String httpMethodName, RouteTemplate template) {
        if (template.isMicronaut()) {
            return of(httpMethod, httpMethodName, template.expression());
        }
        return new EngineRouteDeclaration(httpMethod, httpMethodName, template);
    }
}
