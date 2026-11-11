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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A route template of the URI template language of Micronaut, e.g. {@code /pets/{id}}: the
 * template of the {@code String} templates of the route APIs and of the route annotations. It is a
 * {@link UriMatchTemplate}, matched with a {@link UriTemplateMatcher}; the router matches its
 * routes with the matcher directly.
 *
 * <p>A URI template captures one value per variable name: every occurrence of a repeated name,
 * e.g. {@code /x/{id}/{id}}, has the value the matcher captured for the name, the value of the
 * last occurrence.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public final class UriRouteTemplate implements RouteTemplate {

    /**
     * The parser of the URI templates.
     */
    public static final RouteTemplateParser PARSER = UriRouteTemplate::of;

    private final String expression;
    private final UriMatchTemplate uriMatchTemplate;
    private final UriTemplateMatcher matcher;
    /**
     * Whether the template matches with the matcher: a subclass of {@link UriMatchTemplate}, e.g.
     * {@link UriTypeMatchTemplate}, matches with its own rules.
     */
    private final boolean matchesWithMatcher;
    /**
     * Built when first read: the router matches with the matcher and reads its counts.
     */
    private @Nullable List<RouteTemplateVariable> variables;

    private UriRouteTemplate(String expression, UriMatchTemplate uriMatchTemplate) {
        this.uriMatchTemplate = uriMatchTemplate;
        this.expression = expression;
        this.matcher = new UriTemplateMatcher(uriMatchTemplate.getTemplateString());
        this.matchesWithMatcher = uriMatchTemplate.getClass() == UriMatchTemplate.class;
    }

    /**
     * @param expression A URI template, e.g. {@code /pets/{id}}
     * @return The route template
     */
    public static UriRouteTemplate of(String expression) {
        return new UriRouteTemplate(expression, new UriMatchTemplate(Objects.requireNonNull(expression, "expression")));
    }

    /**
     * @param uriMatchTemplate A URI match template. The route template keeps it, see
     *                         {@link #getUriMatchTemplate()}, and a subclass matches with its own
     *                         rules: such a template cannot be {@link #mount(String) mounted}, e.g.
     *                         under a context path or the prefix of a route group
     * @return The route template
     */
    public static UriRouteTemplate of(UriMatchTemplate uriMatchTemplate) {
        return new UriRouteTemplate(Objects.requireNonNull(uriMatchTemplate, "uriMatchTemplate").toString(), uriMatchTemplate);
    }

    /**
     * @return The URI match template
     */
    public UriMatchTemplate getUriMatchTemplate() {
        return uriMatchTemplate;
    }

    /**
     * @return The matcher the router matches the template with, or {@code null} if the template
     * matches with the rules of a subclass of {@link UriMatchTemplate}
     */
    @Internal
    public @Nullable UriTemplateMatcher matcher() {
        return matchesWithMatcher ? matcher : null;
    }

    @Override
    public String expression() {
        return expression;
    }

    @Override
    public List<RouteTemplateVariable> variables() {
        List<RouteTemplateVariable> result = variables;
        if (result == null) {
            // an immutable list: a race builds it twice, never half
            result = variables(uriMatchTemplate);
            variables = result;
        }
        return result;
    }

    @Override
    public @Nullable RouteCaptures match(String rawPath) {
        UriMatchInfo info = matchesWithMatcher ? matcher.tryMatch(rawPath) : uriMatchTemplate.match(rawPath).orElse(null);
        if (info == null) {
            return null;
        }
        List<RouteTemplateVariable> templateVariables = variables();
        List<@Nullable String> values = new ArrayList<>(templateVariables.size());
        for (RouteTemplateVariable variable : templateVariables) {
            Object value = info.getVariableValues().get(variable.name());
            values.add(value == null ? null : value.toString());
        }
        return RouteCaptures.of(info.getUri(), templateVariables, values);
    }

    /**
     * @throws UnsupportedOperationException for a template of a subclass of {@link UriMatchTemplate}:
     * mounted, it would lose the rules of its class
     */
    @Override
    public RouteTemplate mount(String prefix) {
        if (!matchesWithMatcher) {
            throw new UnsupportedOperationException("The route template " + expression + " of a subclass of UriMatchTemplate cannot be mounted under " + prefix + ": it would lose the rules of its class");
        }
        // the same composition the context path always had
        return of(UriTemplate.of(prefix).nest(expression).toString());
    }

    @Override
    public String requiredPrefix() {
        // the rules of a subclass may match paths the prefix of the template does not start
        return matchesWithMatcher ? matcher.getRequiredPrefix() : "";
    }

    @Override
    public int rawLength() {
        return matcher.getRawLength();
    }

    @Override
    public int pathVariableCount() {
        return matcher.getPathVariableCount();
    }

    @Override
    public int patternVariableCount() {
        return matcher.getPatternVariableCount();
    }

    /**
     * Templates of the same URI template are equal, as {@link UriMatchTemplate} writes it, e.g.
     * {@code /items/} and {@code /items}. A template of a subclass of
     * {@link UriMatchTemplate} is only equal to itself: its rules are not compared.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        // the template as the URI match template writes it, e.g. without a trailing slash: the
        // templates of the two factories that match the same paths are equal
        return o instanceof UriRouteTemplate other && matchesWithMatcher && other.matchesWithMatcher
            && uriMatchTemplate.toString().equals(other.uriMatchTemplate.toString());
    }

    @Override
    public int hashCode() {
        return matchesWithMatcher ? uriMatchTemplate.toString().hashCode() : System.identityHashCode(this);
    }

    @Override
    public String toString() {
        return expression;
    }

    private static List<RouteTemplateVariable> variables(UriMatchTemplate template) {
        // whether each occurrence has a regular expression, in the order of the occurrences
        List<Boolean> patterns = new ArrayList<>();
        for (UriTemplateParser.Part part : UriTemplateParser.parse(template.getTemplateString())) {
            part.visit(new UriTemplateParser.PartVisitor() {
                @Override
                public void visitLiteral(String literal) {
                    // literals have no variables
                }

                @Override
                public void visitExpression(UriTemplateParser.ExpressionType type, List<UriTemplateParser.Variable> expressionVariables) {
                    for (UriTemplateParser.Variable variable : expressionVariables) {
                        patterns.add(isPattern(variable.modifier()));
                    }
                }
            });
        }
        List<UriMatchVariable> occurrences = template.getVariables();
        // the occurrences of the parsed template are the ones of the URI match template: else none is a pattern
        boolean aligned = occurrences.size() == patterns.size();
        List<RouteTemplateVariable> result = new ArrayList<>(occurrences.size());
        for (int i = 0; i < occurrences.size(); i++) {
            UriMatchVariable variable = occurrences.get(i);
            result.add(RouteTemplateVariable.of(
                variable.getName(),
                variable.isOptional(),
                variable.isQuery() ? RouteTemplateVariable.Location.QUERY : RouteTemplateVariable.Location.PATH,
                variable.isExploded(),
                aligned && !variable.isQuery() && patterns.get(i)
            ));
        }
        return List.copyOf(result);
    }

    /**
     * @param modifier The modifier of a variable
     * @return Whether it is a regular expression: a number is a limit of the length of the value
     */
    private static boolean isPattern(@Nullable String modifier) {
        if (StringUtils.isEmpty(modifier)) {
            return false;
        }
        try {
            Integer.parseInt(modifier);
            return false;
        } catch (NumberFormatException e) {
            return true;
        }
    }
}
