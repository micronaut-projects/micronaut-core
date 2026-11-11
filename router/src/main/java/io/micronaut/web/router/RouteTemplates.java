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

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.uri.RouteTemplate;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.http.uri.UriRouteTemplate;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks the templates that are not URI templates when routes are assembled: the
 * router relies on the facts a template describes to select routes.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class RouteTemplates {

    private RouteTemplates() {
    }

    /**
     * Check a template describes the facts the router needs.
     *
     * @param template The template
     * @return The template
     * @throws IllegalStateException if the template describes them incorrectly
     */
    static RouteTemplate checked(RouteTemplate template) {
        if (template instanceof UriRouteTemplate) {
            // correct by construction
            return template;
        }
        List<String> problems = new ArrayList<>(2);
        if (isMissing(template.expression())) {
            problems.add("no expression");
        }
        if (isMissing(template.variables())) {
            problems.add("no variables");
        }
        String prefix = template.requiredPrefix();
        if (isMissing(prefix)) {
            problems.add("no required prefix");
        } else if (!prefix.isEmpty() && prefix.charAt(0) != '/') {
            problems.add("the required prefix '" + prefix + "' does not start with a slash");
        }
        if (template.rawLength() < 0) {
            problems.add("a negative raw length");
        }
        if (template.pathVariableCount() < 0) {
            problems.add("a negative path variable count");
        }
        if (template.patternVariableCount() < 0) {
            problems.add("a negative pattern variable count");
        } else if (template.pathVariableCount() >= 0 && template.patternVariableCount() > template.pathVariableCount()) {
            problems.add("a pattern variable count greater than the path variable count");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("The route template '" + template.expression() + "' (" + template.getClass().getName()
                + ") is described incorrectly for the Micronaut route selection policy: " + String.join(", ", problems));
        }
        return template;
    }

    /**
     * A value a template gave, checked although its declaration does not allow {@code null}: a
     * template of another library may not honour it.
     *
     * @param value The value
     * @return Whether it is {@code null}
     */
    private static boolean isMissing(@Nullable Object value) {
        return value == null;
    }

    /**
     * @param parentTemplate      The URI match template of the enclosing route, or {@code null} if it has another template
     * @param parentRouteTemplate The template of the enclosing route
     * @param child               The nested template
     * @return The composed template
     * @throws IllegalArgumentException if a template is not a URI template, or the nested one is of a subclass of {@link UriMatchTemplate}
     */
    static RouteTemplate nested(@Nullable UriMatchTemplate parentTemplate, RouteTemplate parentRouteTemplate, RouteTemplate child) {
        if (parentTemplate == null || !(child instanceof UriRouteTemplate uriTemplate)) {
            throw notNested(child.expression(), parentRouteTemplate);
        }
        if (uriTemplate.matcher() == null) {
            // only its expression would be nested: the rules of the subclass would be lost
            throw new IllegalArgumentException("The route template " + child.expression() + " of a subclass of UriMatchTemplate cannot be nested in the route template '"
                + parentRouteTemplate.expression() + "'");
        }
        return UriRouteTemplate.of(parentTemplate.nest(uriTemplate.expression()));
    }

    /**
     * @param child  The nested template
     * @param parent The template of the enclosing route
     * @return The error: only URI templates are nested
     */
    static IllegalArgumentException notNested(String child, RouteTemplate parent) {
        return new IllegalArgumentException("Cannot nest the route template '" + child + "' in the route template '"
            + parent.expression() + "': only URI templates are nested");
    }

    /**
     * @param contextPath A context path
     * @return The literal prefix of the context path: with a leading slash, without a trailing
     * one, and {@code null} for none
     */
    static @Nullable String mountPrefix(@Nullable String contextPath) {
        if (contextPath == null || contextPath.isEmpty() || "/".equals(contextPath)) {
            return null;
        }
        String prefix = contextPath.charAt(0) == '/' ? contextPath : '/' + contextPath;
        while (prefix.length() > 1 && prefix.charAt(prefix.length() - 1) == '/') {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        return prefix;
    }
}
