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
package io.micronaut.http.uri.spi;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.uri.ParsedRouteTemplate;
import io.micronaut.http.uri.RoutePattern;
import io.micronaut.http.uri.RouteTemplate;

import java.util.Comparator;
import java.util.Optional;

/**
 * A route template language: parses {@link RouteTemplate}s with its {@link #id() identifier},
 * composes them and creates their matchers.
 *
 * <p>Engines are registered with the Java service loader: a
 * {@code META-INF/services/io.micronaut.http.uri.spi.RouteTemplateEngine} file names the
 * implementation, which needs a public no-argument constructor. The engine of the
 * {@link RouteTemplate#MICRONAUT Micronaut} language is always registered. Identifiers must be
 * unique, see {@link RouteTemplateEngines}.</p>
 *
 * <p>Routes of every engine are selected with the Micronaut route selection policy: the parsed
 * templates must describe the facts it uses, see {@link ParsedRouteTemplate}. An engine whose
 * language has its own order of specificity declares it with {@link #comparator()}: the router
 * uses it between two routes of the engine.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface RouteTemplateEngine {

    /**
     * @return The stable identifier of the language, e.g. {@code micronaut}. Use a namespaced name
     * for a language that is not built in, not the name of the implementation class
     */
    String id();

    /**
     * The version of the semantics of the language as this engine implements it, independent of
     * the version of the artifact. A change to what a template means must change the version.
     *
     * @return The version, e.g. {@code 1.0}
     */
    String version();

    /**
     * Parse a template of this engine.
     *
     * @param template The template, with the identifier of this engine
     * @return The parsed template
     * @throws IllegalArgumentException if the expression is not valid
     */
    ParsedRouteTemplate parse(RouteTemplate template);

    /**
     * Nest a template under another: the template of a route nested in another route.
     *
     * @param parent The template of the enclosing route, parsed by this engine
     * @param child  The nested template, parsed by this engine
     * @return The composed template
     */
    ParsedRouteTemplate nest(ParsedRouteTemplate parent, ParsedRouteTemplate child);

    /**
     * Mount a template under a literal path, e.g. the context path of the server. The prefix is
     * literal text, not an expression of this language.
     *
     * @param prefix   The literal prefix, starting with a slash and not ending with one
     * @param template The template, parsed by this engine
     * @return The mounted template
     */
    ParsedRouteTemplate mount(String prefix, ParsedRouteTemplate template);

    /**
     * Prepare the matcher of a template. Called once per route when the route is built.
     *
     * @param template The template, parsed by this engine
     * @return The matcher
     */
    RoutePattern matcher(ParsedRouteTemplate template);

    /**
     * The order of specificity of the templates of this engine, when it is not the Micronaut
     * one: more literal text first, then fewer variables, then fewer variables constrained by a
     * regular expression. The router uses it only between two routes whose templates are both of
     * this engine, to sort them and to select the most specific of the routes that match a path;
     * a route of this engine and a route of another engine are ordered by the Micronaut policy.
     * For example, JAX-RS prefers more literal characters, then more template variables, then
     * more variables with a regular expression.
     *
     * <p>The comparator must be a total order of the templates of this engine, and
     * {@code compare(a, b) < 0} means {@code a} is more specific than {@code b}. Templates that
     * compare equal are equally specific: a request both match is ambiguous, unless the media
     * types or a route selector decide.</p>
     *
     * @return The order, or empty for the Micronaut order
     */
    default Optional<Comparator<ParsedRouteTemplate>> comparator() {
        return Optional.empty();
    }
}
