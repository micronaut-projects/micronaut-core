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
package io.micronaut.http.uri;

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A route template parsed by its {@link io.micronaut.http.uri.spi.RouteTemplateEngine engine}: an
 * immutable description of the template that routers use without knowing its language.
 *
 * <p>The facts describe the paths the template matches in the <em>matching representation</em> of
 * a path: the path of the request without the query and a trailing slash, see
 * {@link UriTemplateMatcher#normalizeForMatching(String)}. This is currently the only input
 * profile. Captures are not percent-decoded by the pattern; decoding stays with the route match.</p>
 *
 * <p>Engines may carry private state, e.g. their own syntax tree, in their implementations.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface ParsedRouteTemplate {

    /**
     * @return The template: the identifier of the engine and the expression. For a template made by
     * nesting or mounting, the expression the engine composed
     */
    RouteTemplate template();

    /**
     * @return The identifier of the engine that parsed the template
     */
    default String engineId() {
        return template().engineId();
    }

    /**
     * @return The version of the semantics of the engine that parsed the template, see
     * {@link io.micronaut.http.uri.spi.RouteTemplateEngine#version()}
     */
    String engineVersion();

    /**
     * @return The variables of the template, in the order of their occurrence
     */
    List<RouteTemplateVariable> variables();

    /**
     * A literal every path the template matches starts with, in the matching representation of
     * the path. It is a necessary condition only: routers use it to skip templates that cannot
     * match. An empty string when unknown.
     *
     * @return The prefix, or an empty string
     */
    String requiredPrefix();

    /**
     * The length of the literal parts of the template: the first key of the specificity of routes
     * under the Micronaut route selection policy, where more literal text is more specific.
     *
     * @return The length
     */
    int rawLength();

    /**
     * The number of variables of the path: the second key of the specificity of routes under the
     * Micronaut route selection policy, where fewer variables are more specific.
     *
     * @return The count
     */
    int pathVariableCount();

    /**
     * The number of variables of the path constrained by a regular expression: the third key of
     * the specificity of routes under the Micronaut route selection policy, where fewer such
     * variables are more specific when the literal length and the variable count tie. For a
     * Micronaut template {@link UriTemplateMatcher#getPatternVariableCount()}.
     *
     * @return The count
     */
    int patternVariableCount();

    /**
     * The segments of the path the template matches, which the router compares to decide
     * whether two templates can match the same path. {@code null} when the engine cannot tell:
     * the template is then assumed to overlap every other template.
     *
     * @return The segments, or {@code null} if unknown
     */
    @Nullable List<RouteTemplateSegment> pathSegments();
}
