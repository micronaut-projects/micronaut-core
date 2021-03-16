/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.validation;

import io.micronaut.core.annotation.Internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Deprecated
@Internal
public final class InternalUriMatchTemplate extends InternalUriTemplate {

    protected static final String VARIABLE_MATCH_PATTERN = "([^\\/\\?#&;\\+]";
    protected StringBuilder pattern;
    protected List<InternalUriMatchVariable> variables;

    /**
     * Construct a new URI template for the given template.
     *
     * @param templateString The template string
     */
    public InternalUriMatchTemplate(CharSequence templateString) {
        this(templateString, new Object[0]);
    }

    /**
     * Construct a new URI template for the given template.
     *
     * @param templateString  The template string
     * @param parserArguments The parsed arguments
     */
    protected InternalUriMatchTemplate(CharSequence templateString, Object... parserArguments) {
        super(templateString, parserArguments);
    }

    /**
     * @return The variables this template expects
     */
    public List<String> getVariableNames() {
        return variables.stream().map(InternalUriMatchVariable::getName).collect(Collectors.toList());
    }

    public List<InternalUriMatchVariable> getVariables() {
        return variables;
    }

    /**
     * Create a new {@link InternalUriTemplate} for the given URI.
     *
     * @param uri The URI
     * @return The template
     */
    public static InternalUriMatchTemplate of(String uri) {
        return new InternalUriMatchTemplate(uri);
    }

    @Override
    protected UriTemplateParser createParser(String templateString, Object... parserArguments) {

        if (Objects.isNull(this.pattern)) {
            this.pattern = new StringBuilder();
        }

        if (this.variables == null) {
            this.variables = new ArrayList<>();
        }
        return new InternalUriMatchTemplate.UriMatchTemplateParser(templateString, this);
    }

    /**
     * <p>Extended version of {@link InternalUriTemplate.UriTemplateParser} that builds a regular expression to match a path.
     * Note that fragments (#) and queries (?) are ignored for the purposes of matching.</p>
     */
    protected static class UriMatchTemplateParser extends UriTemplateParser {

        final InternalUriMatchTemplate matchTemplate;

        /**
         * @param templateText  The template
         * @param matchTemplate The Uri match template
         */
        protected UriMatchTemplateParser(String templateText, InternalUriMatchTemplate matchTemplate) {
            super(templateText);
            this.matchTemplate = matchTemplate;
        }

        /**
         * @return The URI match template
         */
        public InternalUriMatchTemplate getMatchTemplate() {
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
            matchTemplate.variables.add(new InternalUriMatchVariable(variable, modifierChar, operator));
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

