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
 * A parsed route template: which paths a route matches. The router uses the template as it is
 * given, so a library can bring its own template language: it implements this interface, and
 * declares its routes with its templates, or with a {@link RouteTemplateParser} and the text of the
 * templates. The {@code String} templates of the route APIs and of the route annotations are
 * {@link UriRouteTemplate URI templates}, e.g. {@code /pets/{id}}.
 *
 * <p>A template is immutable and safe for concurrent use: {@link #match(String)} is called for
 * every request the route is a candidate for.</p>
 *
 * <p>Routes of different template languages share one route table. The router orders them, and
 * resolves the requests that several of them match, by the facts the templates describe: more
 * literal text first ({@link #rawLength()}), then fewer path variables
 * ({@link #pathVariableCount()}), then fewer path variables constrained by a regular expression
 * ({@link #patternVariableCount()}).</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface RouteTemplate {

    /**
     * The expression of the template in its language, e.g. for the routes endpoint, the metrics
     * and {@link io.micronaut.http.BasicHttpAttributes#getUriTemplate}. It is for display: it is
     * not parsed again. Templates are equal when they match the same paths with the same
     * variables: implementations define {@link Object#equals(Object)} and
     * {@link Object#hashCode()}, e.g. on the expression; the router uses them to find the
     * {@code GET} routes that have an explicit {@code HEAD} route.
     *
     * @return The expression
     */
    String expression();

    /**
     * @return The variables of the template: every occurrence, in order, an immutable list
     */
    List<RouteTemplateVariable> variables();

    /**
     * Match a path. The input is the raw, not percent-decoded path of a request, without its
     * query, which may have a trailing slash. The template applies the rules of its language, e.g.
     * ignores the matrix parameters of the segments, and matches the complete path. The router
     * calls it for every request the route is a candidate for: an implementation prepares its
     * matcher when it is created, and returns {@link RouteCaptures#of} with its
     * {@link #variables()}.
     *
     * @param rawPath The path of the request
     * @return The captures of the variables, not percent-decoded, in the order of
     * {@link #variables()}, or {@code null} if the path does not match. A language that captures one
     * value per name gives that value to every occurrence of the name
     */
    @Nullable RouteCaptures match(String rawPath);

    /**
     * Match a prefix of a path, as the prefix of a locator route matches: the longest prefix of
     * the path that ends before a slash of the path or at its end, or the root {@code /}, that the
     * template matches as it {@link #match(String) matches} a complete path. The input is the raw
     * path of a request, without its query.
     *
     * <p>By default, the template matches the path, then the prefixes before each of its slashes,
     * from the longest. A language that finds where a match of the template ends in one pass
     * overrides it, e.g. by matching segment by segment.</p>
     *
     * @param rawPath The path of the request
     * @return The captures of the variables, whose {@link RouteCaptures#path() path} is the matched
     * prefix of the path, exactly as given: the rest of the path follows it after a slash. Or
     * {@code null} if no prefix matches
     */
    default @Nullable RouteCaptures matchPrefix(String rawPath) {
        int end = rawPath.length();
        while (true) {
            String prefix = end == 0 ? "/" : rawPath.substring(0, end);
            RouteCaptures captures = match(prefix);
            if (captures != null) {
                return prefix.equals(captures.path()) ? captures : RouteCaptures.of(prefix, captures.variables(), captures.values());
            }
            if (end == 0) {
                return null;
            }
            end = rawPath.lastIndexOf('/', end - 1);
            if (end < 0) {
                return null;
            }
        }
    }

    /**
     * Mount this template under a literal path, e.g. the context path of the server or the prefix
     * of a route group. The prefix is literal text, not an expression of the language. The router
     * mounts a declared template once, and an implementation should reuse what it already
     * prepared rather than parse its expression again.
     *
     * @param prefix The literal prefix, starting with a slash and not ending with one
     * @return The mounted template
     */
    RouteTemplate mount(String prefix);

    /**
     * A literal every path the template matches starts with: the raw path without its query and
     * without a trailing slash, the path the route index looks up. It is a necessary condition
     * only: the router uses it to skip the templates that cannot match a path, and does not rely
     * on a trailing slash of the prefix. A template without a prefix is a candidate for every
     * request of its method, and is matched for each of them: give the longest prefix the
     * language allows.
     *
     * @return The prefix, starting with a slash, or an empty string when unknown
     */
    default String requiredPrefix() {
        return "";
    }

    /**
     * The length of the literal text of the template, the first key of the order of routes: more
     * literal text is more specific.
     *
     * @return The length
     */
    int rawLength();

    /**
     * The number of variables of the path, the second key of the order of routes: fewer variables
     * are more specific. By default the {@link RouteTemplateVariable.Location#PATH path}
     * {@link #variables()}.
     *
     * @return The count
     */
    default int pathVariableCount() {
        int count = 0;
        for (RouteTemplateVariable variable : variables()) {
            if (variable.location() == RouteTemplateVariable.Location.PATH) {
                count++;
            }
        }
        return count;
    }

    /**
     * The number of variables of the path constrained by a regular expression, the third key of
     * the order of routes: fewer such variables are more specific. It only orders routes: a
     * request two routes match equally otherwise stays ambiguous. By default the
     * {@link RouteTemplateVariable#pattern() pattern} path {@link #variables()}.
     *
     * @return The count
     */
    default int patternVariableCount() {
        int count = 0;
        for (RouteTemplateVariable variable : variables()) {
            if (variable.location() == RouteTemplateVariable.Location.PATH && variable.pattern()) {
                count++;
            }
        }
        return count;
    }
}
