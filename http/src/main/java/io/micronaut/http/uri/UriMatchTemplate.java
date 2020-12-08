/*
 * Copyright 2017-2020 original authors
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Extends {@link UriTemplate} and adds the ability to match a URI to a given template using the
 * {@link #match(java.net.URI)} method.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class UriMatchTemplate extends UriTemplate implements UriMatcher {

    protected static final String VARIABLE_MATCH_PATTERN = "([^\\/\\?#&;\\+]";
    protected StringBuilder pattern;
    protected List<UriMatchVariable> variables;
    private final Pattern matchPattern;
    private final boolean isRoot;

    /**
     * Construct a new URI template for the given template.
     *
     * @param templateString The template string
     */
    public UriMatchTemplate(CharSequence templateString) {
        this(templateString, new Object[0]);
    }

    /**
     * Construct a new URI template for the given template.
     *
     * @param templateString  The template string
     * @param parserArguments The parsed arguments
     */
    protected UriMatchTemplate(CharSequence templateString, Object... parserArguments) {
        super(templateString, parserArguments);
        this.matchPattern = Pattern.compile(pattern.toString());
        this.isRoot = isRoot();
        // cleanup / reduce memory consumption
        this.pattern = null;
    }

    /**
     * @param templateString    The template
     * @param segments          The list of segments
     * @param matchPattern      The match pattern
     * @param variables         The variables
     */
    protected UriMatchTemplate(CharSequence templateString, List<PathSegment> segments, Pattern matchPattern, List<UriMatchVariable> variables) {
        super(templateString.toString(), segments);
        this.matchPattern = matchPattern;
        this.variables = variables;
        this.isRoot = isRoot();
    }

    /**
     * @param uriTemplate       The template
     * @param newSegments       The list of new segments
     * @param newPattern        The list of new patters
     * @param variables         The variables
     * @return An instance of {@link UriMatchTemplate}
     */
    protected UriMatchTemplate newUriMatchTemplate(CharSequence uriTemplate, List<PathSegment> newSegments, Pattern newPattern, List<UriMatchVariable> variables) {
        return new UriMatchTemplate(uriTemplate, newSegments, newPattern, variables);
    }

    /**
     * @return The variables this template expects
     */
    public List<String> getVariableNames() {
        return variables.stream().map(UriMatchVariable::getName).collect(Collectors.toList());
    }

    /**
     * @return The variables this template expects
     */
    public List<UriMatchVariable> getVariables() {
        return Collections.unmodifiableList(variables);
    }

    /**
     * Returns the path string excluding any query variables.
     *
     * @return The path string
     */
    public String toPathString() {
        return toString(pathSegment -> {
            final Optional<String> var = pathSegment.getVariable();
            if (var.isPresent()) {
                final Optional<UriMatchVariable> umv = variables.stream()
                        .filter(v -> v.getName().equals(var.get())).findFirst();
                if (umv.isPresent()) {
                    final UriMatchVariable uriMatchVariable = umv.get();
                    if (uriMatchVariable.isQuery()) {
                        return false;
                    }
                }
            }
            return true;
        });
    }

    /**
     * Match the given URI string.
     *
     * @param uri The uRI
     * @return True if it matches
     */
    @Override
    public Optional<UriMatchInfo> match(String uri) {
        if (uri == null) {
            throw new IllegalArgumentException("Argument 'uri' cannot be null");
        }
        int length = uri.length();
        if (length > 1 && uri.charAt(length - 1) == '/') {
            uri = uri.substring(0, length - 1);
        }

        if (isRoot && (length == 0 || (length == 1 && uri.charAt(0) == '/'))) {
            return Optional.of(new DefaultUriMatchInfo(uri, Collections.emptyMap(), variables));
        }
        //Remove any url parameters before matching
        int parameterIndex = uri.indexOf('?');
        if (parameterIndex > -1) {
            uri = uri.substring(0, parameterIndex);
        }
        if (uri.endsWith("/")) {
            uri = uri.substring(0, uri.length() - 1);
        }
        Matcher matcher = matchPattern.matcher(uri);
        if (matcher.matches()) {
            if (variables.isEmpty()) {
                return Optional.of(new DefaultUriMatchInfo(uri, Collections.emptyMap(), variables));
            } else {
                int count = matcher.groupCount();
                Map<String, Object> variableMap = new LinkedHashMap<>(count);
                for (int j = 0; j < variables.size(); j++) {
                    int index = (j * 2) + 2;
                    if (index > count) {
                        break;
                    }
                    UriMatchVariable variable = variables.get(j);
                    String value = matcher.group(index);
                    variableMap.put(variable.getName(), value);
                }
                return Optional.of(new DefaultUriMatchInfo(uri, variableMap, variables));
            }
        }
        return Optional.empty();
    }

    @Override
    public UriMatchTemplate nest(CharSequence uriTemplate) {
        return (UriMatchTemplate) super.nest(uriTemplate);
    }

    /**
     * Create a new {@link UriTemplate} for the given URI.
     *
     * @param uri The URI
     * @return The template
     */
    public static UriMatchTemplate of(String uri) {
        return new UriMatchTemplate(uri);
    }

    @Override
    protected UriTemplate newUriTemplate(CharSequence uriTemplate, List<PathSegment> newSegments) {
        Pattern newPattern = Pattern.compile(this.matchPattern.toString() + pattern.toString());
        pattern = null;
        return newUriMatchTemplate(normalizeNested(toString(), uriTemplate), newSegments, newPattern, new ArrayList<>(variables));
    }

    @Override
    protected UriTemplateParser createParser(String templateString, Object... parserArguments) {

        if (Objects.isNull(this.pattern)) {
            this.pattern = new StringBuilder();
        }

        if (this.variables == null) {
            this.variables = new ArrayList<>();
        }
        return new UriMatchTemplateParser(templateString, this);
    }

    private boolean isRoot() {
        CharSequence rawSegment = null;
        for (PathSegment segment : segments) {
            if (segment.isVariable()) {
                if (!segment.isQuerySegment()) {
                    return false;
                }
            } else {
                if (rawSegment == null) {
                    rawSegment = segment;
                } else {
                    return false;
                }
            }
        }
        if (rawSegment == null) {
            return true;
        } else {
            int len = rawSegment.length();
            return len == 0 || (len == 1 && rawSegment.charAt(0) == '/');
        }
    }

    /**
     * The default {@link UriMatchInfo} implementation.
     */
    protected static class DefaultUriMatchInfo implements UriMatchInfo {

        private final String uri;
        private final Map<String, Object> variableValues;
        private final List<UriMatchVariable> variables;
        private final Map<String, UriMatchVariable> variableMap;

        /**
         * @param uri            The URI
         * @param variableValues The map of variable names with values
         * @param variables      The variables
         */
        protected DefaultUriMatchInfo(String uri, Map<String, Object> variableValues, List<UriMatchVariable> variables) {
            this.uri = uri;
            this.variableValues = variableValues;
            this.variables = variables;
            LinkedHashMap<String, UriMatchVariable> vm = new LinkedHashMap<>(variables.size());
            for (UriMatchVariable variable : variables) {
                vm.put(variable.getName(), variable);
            }
            this.variableMap = Collections.unmodifiableMap(vm);
        }

        @Override
        public String getUri() {
            return uri;
        }

        @Override
        public Map<String, Object> getVariableValues() {
            return variableValues;
        }

        @Override
        public List<UriMatchVariable> getVariables() {
            return Collections.unmodifiableList(variables);
        }

        @Override
        public Map<String, UriMatchVariable> getVariableMap() {
            return variableMap;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            DefaultUriMatchInfo that = (DefaultUriMatchInfo) o;
            return uri.equals(that.uri) && variables.equals(that.variables);
        }

        @Override
        public String toString() {
            return getUri();
        }

        @Override
        public int hashCode() {
            int result = uri.hashCode();
            result = 31 * result + variables.hashCode();
            return result;
        }
    }

    /**
     * <p>Extended version of {@link UriTemplate.UriTemplateParser} that builds a regular expression to match a path.
     * Note that fragments (#) and queries (?) are ignored for the purposes of matching.</p>
     */
    protected static class UriMatchTemplateParser extends UriTemplateParser {

        final UriMatchTemplate matchTemplate;

        /**
         * @param templateText  The template
         * @param matchTemplate The Uri match template
         */
        protected UriMatchTemplateParser(String templateText, UriMatchTemplate matchTemplate) {
            super(templateText);
            this.matchTemplate = matchTemplate;
        }

        /**
         * @return The URI match template
         */
        public UriMatchTemplate getMatchTemplate() {
            return matchTemplate;
        }

        @Override
        protected void addRawContentSegment(List<PathSegment> segments, String value, boolean isQuerySegment) {
            matchTemplate.pattern.append(Pattern.quote(value));
            super.addRawContentSegment(segments, value, isQuerySegment);
        }

        @Override
        protected void addVariableSegment(List<PathSegment> segments,
                                          String variable,
                                          String prefix,
                                          String delimiter,
                                          boolean encode,
                                          boolean repeatPrefix,
                                          String modifierStr,
                                          char modifierChar,
                                          char operator,
                                          String previousDelimiter, boolean isQuerySegment) {
            matchTemplate.variables.add(new UriMatchVariable(variable, modifierChar, operator));
            StringBuilder pattern = matchTemplate.pattern;
            int modLen = modifierStr.length();
            boolean hasModifier = modifierChar == ':' && modLen > 0;
            String operatorPrefix = "";
            String operatorQuantifier = "";
            String variableQuantifier = "+?)";
            String variablePattern = getVariablePattern(variable, operator);
            if (hasModifier) {
                char firstChar = modifierStr.charAt(0);
                if (firstChar == '?') {
                    operatorQuantifier = "";
                } else if (modifierStr.chars().allMatch(Character::isDigit)) {
                    variableQuantifier = "{1," + modifierStr + "})";
                } else {

                    char lastChar = modifierStr.charAt(modLen - 1);
                    if (lastChar == '*' ||
                        (modLen > 1 && lastChar == '?' && (modifierStr.charAt(modLen - 2) == '*' || modifierStr.charAt(modLen - 2) == '+'))) {
                        operatorQuantifier = "?";
                    }
                    if (operator == '/' || operator == '.') {
                        variablePattern = "(" + ((firstChar == '^') ? modifierStr.substring(1) : modifierStr) + ")";
                    } else {
                        operatorPrefix = "(";
                        variablePattern = ((firstChar == '^') ? modifierStr.substring(1) : modifierStr) + ")";
                    }
                    variableQuantifier = "";
                }
            }

            boolean operatorAppended = false;

            switch (operator) {
                case '.':
                case '/':
                    pattern.append("(")
                        .append(operatorPrefix)
                        .append("\\")
                        .append(String.valueOf(operator))
                        .append(operatorQuantifier);
                    operatorAppended = true;
                case '+':
                case '0': // no active operator
                    if (!operatorAppended) {
                        pattern.append("(").append(operatorPrefix);
                    }
                    pattern.append(variablePattern)
                        .append(variableQuantifier)
                        .append(")");
                    break;
                default:
                    // no-op
            }

            if (operator == '/' || modifierStr.equals("?")) {
                pattern.append("?");
            }
            super.addVariableSegment(segments, variable, prefix, delimiter, encode, repeatPrefix, modifierStr, modifierChar, operator, previousDelimiter, isQuerySegment);
        }

        /**
         * @param variable The variable
         * @param operator The operator
         * @return The variable match pattern
         */
        protected String getVariablePattern(String variable, char operator) {
            if (operator == '+') {
                // Allow reserved characters. See https://tools.ietf.org/html/rfc6570#section-3.2.3
                return "([\\S]";
            } else {
                return VARIABLE_MATCH_PATTERN;
            }
        }
    }
}
