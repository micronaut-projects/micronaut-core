/*
 * Copyright 2017-2024 original authors
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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation of the paths matching <a href="https://tools.ietf.org/html/rfc6570">rfc6570</a>.
 *
 * @author Denis Stepanov
 * @since 4.6.0
 */
@Internal
public final class UriTemplateMatcher implements UriMatcher, Comparable<UriTemplateMatcher> {

    private final String templateString;
    private final List<UriTemplateParser.Part> parts;
    private final List<UriMatchVariable> variables;
    private final Segment[] segments;
    private final boolean isRoot;

    // Matches cache
    private UriMatchInfo rootMatchInfo;
    private UriMatchInfo exactMatchInfo;

    /**
     * Construct a new URI template for the given template.
     *
     * @param templateString The template string
     */
    public UriTemplateMatcher(String templateString) {
        this(templateString, UriTemplateParser.parse(templateString));
    }

    /**
     * Construct a new URI template for the given template.
     *
     * @param parts The parsed parts
     */
    private UriTemplateMatcher(String templateString, List<UriTemplateParser.Part> parts) {
        this.templateString = templateString;
        this.parts = parts;
        List<UriMatchVariable> variables = new ArrayList<>();
        this.segments = provideMatchSegments(parts, variables);
        this.isRoot = segments.length == 0 || segments.length == 1 && segments[0].type == SegmentType.LITERAL && isRoot(segments[0].value);
        this.variables = Collections.unmodifiableList(variables);
    }

    private static Segment[] provideMatchSegments(List<UriTemplateParser.Part> parts, List<UriMatchVariable> variables) {
        List<Segment> segments = new ArrayList<>();
        List<String> regexpVariables = new ArrayList<>();
        StringBuilder regexp = null;
        for (int i = 0; i < parts.size(); i++) {
            UriTemplateParser.Part part = parts.get(i);
            if (part instanceof UriTemplateParser.Literal literal) {
                if (regexp == null) {
                    segments.add(new Segment(SegmentType.LITERAL, literal.text(), null, null));
                } else {
                    regexp.append(Pattern.quote(literal.text()));
                }
            } else if (part instanceof UriTemplateParser.Expression expression) {
                if (regexp == null && allowPathSegment(expression, parts, i)) {
                    for (UriTemplateParser.Variable variable : expression.variables()) {
                        variables.add(new UriMatchVariable(
                                variable.name(),
                                variable.explode() ? '*' : '0',
                                expression.type().getOperator()
                            )
                        );
                        segments.add(new Segment(SegmentType.PATH, variable.name(), null, null));
                    }
                    continue;
                }
                if (regexp == null) {
                    regexp = new StringBuilder();
                }
                for (UriTemplateParser.Variable variable : expression.variables()) {
                    variables.add(new UriMatchVariable(
                            variable.name(),
                            variable.explode() ? '*' : '0',
                            expression.type().getOperator()
                        )
                    );
                    appendRegexp(regexp, expression.type(), variable, regexpVariables);
                }
            }
        }
        if (regexp != null) {
            segments.add(new Segment(SegmentType.REGEXP, null, Pattern.compile(regexp.toString()), regexpVariables.toArray(String[]::new)));
        }

        return segments.toArray(Segment[]::new);
    }

    private static boolean allowPathSegment(UriTemplateParser.Expression expression,
                                     List<UriTemplateParser.Part> parts,
                                     int index) {
        if (expression.type() != UriTemplateParser.ExpressionType.NONE) {
            return false; // Only this on is supported
        }
        if (!expression.variables().stream().allMatch(v -> v.modifier() == null)) {
            return false; // Cannot have any kind of pattern
        }
        if (parts.size() == index + 1) {
            return true; // Last path
        }
        if (parts.get(index + 1) instanceof UriTemplateParser.Literal literal && literal.text().startsWith("/")) {
            return true; // It can absorb everything till the next one
        }
        return false;
    }

    @SuppressWarnings("MissingSwitchDefault")
    private static void appendRegexp(StringBuilder regexpBuilder,
                                     UriTemplateParser.ExpressionType type,
                                     UriTemplateParser.Variable variable,
                                     List<String> variables) {

        switch (type) {
            case PATH_STYLE_PARAMETER_EXPANSION:
            case FORM_STYLE_PARAMETER_EXPANSION:
            case FORM_STYLE_QUERY_CONTINUATION:
            case FRAGMENT_EXPANSION:
                return; // Unsupported types
        }

        Integer limit = null;
        String pattern = null;
        String modifier = variable.modifier();
        if (StringUtils.isNotEmpty(modifier)) {
            try {
                limit = Integer.parseInt(modifier);
            } catch (Exception ignore) {
                // Ignore
            }
            if (limit == null) {
                pattern = modifier;
            }
        }

        // Code originally from UriMatchTemplate

        String operatorPrefix = "";
        String operatorQuantifier = "";
        String variableQuantifier = "+?)";
        String variablePattern = null;
        if (pattern != null) {
            char firstChar = pattern.charAt(0);
            if (firstChar == '?') {
                operatorQuantifier = "";
            } else {
                int patternLength = pattern.length();
                char lastChar = pattern.charAt(patternLength - 1);
                if (lastChar == '*' ||
                    (patternLength > 1 && lastChar == '?'
                        && (pattern.charAt(patternLength - 2) == '*' || pattern.charAt(patternLength - 2) == '+'))) {
                    operatorQuantifier = "?";
                }
                String s = (firstChar == '^') ? pattern.substring(1) : pattern;
                char operator = type.getOperator();
                if (operator == '/' || operator == '.') {
                    variablePattern = "(" + s + ")";
                } else {
                    operatorPrefix = "(";
                    variablePattern = s + ")";
                }
                variableQuantifier = StringUtils.EMPTY_STRING;
            }
        } else if (limit != null) {
            variableQuantifier = "{1," + limit + "})";
        }

        variables.add(variable.name());

        boolean operatorAppended = false;
        switch (type) {
            case LABEL_EXPANSION:
            case PATH_SEGMENT_EXPANSION:
                regexpBuilder
                    .append('(')
                    .append(operatorPrefix)
                    .append('\\')
                    .append(type.getOperator())
                    .append(operatorQuantifier);
                operatorAppended = true;
                // fall through
            case RESERVED_EXPANSION:
            case NONE:
                if (!operatorAppended) {
                    regexpBuilder.append('(').append(operatorPrefix);
                }
                if (variablePattern == null) {
                    if (type == UriTemplateParser.ExpressionType.RESERVED_EXPANSION) {
                        // Allow reserved characters. See https://tools.ietf.org/html/rfc6570#section-3.2.3
                        variablePattern = "([\\S]";
                    } else {
                        variablePattern = "([^/?#(!{)&;+]";
                    }
                }
                regexpBuilder
                    .append(variablePattern)
                    .append(variableQuantifier)
                    .append(')');
                break;
            default:
                throw new IllegalStateException("Unsupported regexp expression type: " + type);
        }
        if (type == UriTemplateParser.ExpressionType.PATH_SEGMENT_EXPANSION || pattern != null && pattern.equals("?")) {
            regexpBuilder.append('?');
        }
    }

    /**
     * Match the given URI string.
     *
     * @param uri The uRI
     * @return an optional match
     */
    @Override
    public Optional<UriMatchInfo> match(String uri) {
        return Optional.ofNullable(tryMatch(uri));
    }

    /**
     * Match the given URI string.
     *
     * @param uri The uRI
     * @return a match or null
     */
    @Nullable
    public UriMatchInfo tryMatch(@NonNull String uri) {
        int length = uri.length();
        if (length > 1 && uri.charAt(length - 1) == '/') {
            uri = uri.substring(0, length - 1);
        }
        if (isRoot && isRoot(uri)) {
            if (rootMatchInfo == null) {
                rootMatchInfo = new DefaultUriMatchInfo(uri, Collections.emptyMap(), variables);
            }
            return rootMatchInfo;
        }
        // Remove any url parameters before matching
        int parameterIndex = uri.indexOf('?');
        if (parameterIndex > -1) {
            uri = uri.substring(0, parameterIndex);
            length = uri.length();
            if (length > 1 && uri.charAt(length - 1) == '/') {
                uri = uri.substring(0, length - 1);
            }
        }
        if (variables.isEmpty()) {
            if (uri.equals(templateString)) {
                if (exactMatchInfo == null) {
                    exactMatchInfo = new DefaultUriMatchInfo(uri, Collections.emptyMap(), variables);
                }
                return exactMatchInfo;
            }
            return null;
        }
        Map<String, Object> variableMap = CollectionUtils.newLinkedHashMap(variables.size());
        if (match(uri, variableMap)) {
            return new DefaultUriMatchInfo(uri, variableMap, variables);
        }
        return null;
    }

    private boolean match(String uri, Map<String, Object> variableMap) {
        for (int i = 0; i < segments.length; i++) {
            Segment segment = segments[i];
            switch (segment.type) {
                case LITERAL -> {
                    if (uri.startsWith(segment.value)) {
                        uri = uri.substring(segment.value.length());
                    } else {
                        return false;
                    }
                }
                case PATH -> {
                    boolean requiresSlash = i + 1 != segments.length;
                    int index = readText(uri, requiresSlash);
                    if (index > 0) { // Deny empty path
                        String path = uri.substring(0, index);
                        variableMap.put(segment.value, path);
                        uri = uri.substring(index);
                    } else {
                        return false;
                    }
                }
                case REGEXP -> {
                    Matcher matcher = segment.pattern.matcher(uri);
                    if (matcher.matches()) {
                        int groupInx = 2;
                        for (String matchingVariable : segment.regexpVariables) {
                            String group = matcher.group(groupInx);
                            variableMap.put(matchingVariable, group);
                            groupInx += 2;
                        }
                        return true;
                    } else {
                        return false;
                    }
                }
                default -> throw new IllegalStateException("Unsupported segment type: " + segment.type);
            }
        }
        return uri.isEmpty();
    }

    private static int readText(String input, boolean requiresSlash) {
        // NOTE: Micronaut doesn't allow some of the character in the path value
        int length = input.length();
        for (int i = 0; i < length; i++) {
            char c = input.charAt(i);
            if (requiresSlash && c == '/') {
                return i;
            }
            if (rejectCharacter(c, input, i)) {
                return -1;
            }
        }
        return length;
    }

    private static boolean rejectCharacter(char c, String input, int i) {
        switch (c) {
            case '/':
            case '?':
            case '{':
            case '}':
            case '&':
            case ';':
            case '+':
                return true;
            case '#':
                if (i + 1 < input.length()) {
                    c = input.charAt(i + 1);
                    if (c != '{') {
                        return true;
                    }
                }
            default:
                return false;
        }
    }

    /**
     * Nests another URI template with this template.
     *
     * @param uriTemplate The URI template. If it does not begin with forward slash it will automatically be appended with forward slash
     * @return The new URI template
     */
    public UriTemplateMatcher nest(CharSequence uriTemplate) {
        List<UriTemplateParser.Part> newParts = UriTemplateParser.parse(uriTemplate.toString());
        return new UriTemplateMatcher(templateString + uriTemplate, UriTemplateParser.concat(parts, newParts));
    }

    /**
     * Returns the path string excluding any query variables.
     *
     * @return The path string
     */
    public String toPathString() {
        StringBuilder builder = new StringBuilder();
        visitParts(parts, new UriTemplateParser.PartVisitor() {
            @Override
            public void visitLiteral(String literal) {
                builder.append(literal);
            }

            @Override
            public void visitExpression(UriTemplateParser.ExpressionType type, List<UriTemplateParser.Variable> variables) {
                builder.append('{');
                if (type != UriTemplateParser.ExpressionType.NONE) {
                    builder.append(type.getOperator());
                }
                for (Iterator<UriTemplateParser.Variable> iterator = variables.iterator(); iterator.hasNext(); ) {
                    UriTemplateParser.Variable variable = iterator.next();
                    builder.append(variable.name());
                    if (variable.explode()) {
                        builder.append('*');
                    }
                    if (variable.modifier() != null) {
                        builder.append(':');
                        builder.append(variable.modifier());
                    }
                    if (iterator.hasNext()) {
                        builder.append(',');
                    }
                }
                builder.append('}');
            }
        });
        return builder.toString();
    }

    /**
     * Expand the string with the given parameters.
     *
     * @param parameters The parameters
     * @return The expanded URI
     */
    public String expand(Map<String, Object> parameters) {
        UriTemplateExpander uriTemplateExpander = new UriTemplateExpander(parameters);
        visitParts(parts, uriTemplateExpander);
        return uriTemplateExpander.toString();
    }

    @Override
    public int compareTo(UriTemplateMatcher o) {
        if (this == o) {
            return 0;
        }

        PathEvaluator thisEvaluator = new PathEvaluator();
        PathEvaluator thatEvaluator = new PathEvaluator();

        visitParts(parts, thisEvaluator);
        visitParts(o.parts, thatEvaluator);

        // using that.compareTo because more raw length should have higher precedence
        int rawCompare = Integer.compare(thatEvaluator.rawLength, thisEvaluator.rawLength);
        if (rawCompare == 0) {
            return Integer.compare(thisEvaluator.variableCount, thatEvaluator.variableCount);
        }
        return rawCompare;
    }

    @Override
    public String toString() {
        return toPathString();
    }

    private static void visitParts(List<UriTemplateParser.Part> parts, UriTemplateParser.PartVisitor visitor) {
        for (UriTemplateParser.Part part : parts) {
            part.visit(visitor);
        }
    }

    private boolean isRoot(String uri) {
        int length = uri.length();
        return length == 0 || length == 1 && uri.charAt(0) == '/';
    }

    /**
     * /**
     * Create a new {@link UriTemplate} for the given URI.
     *
     * @param uri The URI
     * @return The template
     */
    public static UriTemplateMatcher of(String uri) {
        return new UriTemplateMatcher(uri);
    }

    private static final class PathEvaluator implements UriTemplateParser.PartVisitor {

        int variableCount = 0;
        int rawLength = 0;

        @Override
        public void visitLiteral(String literal) {
            rawLength += literal.length();
        }

        @Override
        public void visitExpression(UriTemplateParser.ExpressionType type, List<UriTemplateParser.Variable> variables) {
            if (!type.isQueryPart()) {
                variableCount += variables.size();
            }
        }
    }

    private record Segment(SegmentType type, String value,
                           Pattern pattern, String[] regexpVariables) {
    }

    private enum SegmentType {
        LITERAL, PATH, REGEXP
    }

}
