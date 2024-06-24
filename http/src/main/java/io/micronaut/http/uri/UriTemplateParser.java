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
import io.micronaut.core.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The URI template parser <a href="https://tools.ietf.org/html/rfc6570">rfc6570</a>.
 *
 * @author Denis Stepanov
 * @since 4.6.0
 */
@Internal
final class UriTemplateParser {

    private UriTemplateParser() {
    }

    /**
     * Parse the template according to the spec.
     *
     * @param template The template
     * @return The parts of the template
     */
    public static List<Part> parse(String template) {
        List<Part> parts = new ArrayList<>(10);
        int expressionStartIndex = -1;
        char[] input = template.toCharArray();
        StringBuilder literal = new StringBuilder();
        boolean isText = true;
        for (int i = 0; i < input.length; i++) {
            char c = input[i];
            if (c == '{') {
                isText = false;
                if (!literal.isEmpty()) {
                    parts.add(new Literal(literal.toString()));
                    literal.setLength(0);
                }
                expressionStartIndex = i;
            } else if (c == '}' && expressionStartIndex != -1) {
                Expression expression = parseExpression(input, expressionStartIndex + 1, i);
                parts.add(expression);
                isText = true;
            } else if (isText) {
                if (Character.isISOControl(c) || Character.isWhitespace(c) || isAllowedCharacter(c) || c == '%') {
                    literal.append(c);
                } else {
                    throw new IllegalStateException("Unexpected character '" + c + "' at position " + i + " in " + template);
                }
            }
        }
        if (!literal.isEmpty()) {
            parts.add(new Literal(literal.toString()));
            literal.setLength(0);
        }
        return parts;
    }

    /**
     * Concat two collections of parts. Properly merging `/`.
     *
     * @param parts1 The first collection
     * @param parts2 The second collection
     * @return the merged collection
     */
    public static List<UriTemplateParser.Part> concat(List<UriTemplateParser.Part> parts1, List<UriTemplateParser.Part> parts2) {
        parts1 = new ArrayList<>(parts1);
        parts2 = new ArrayList<>(parts2);
        List<UriTemplateParser.Part> queryParams = new ArrayList<>();
        List<UriTemplateParser.Part> fragmentParams = new ArrayList<>();
        // Query params should be last
        removeQueryParams(parts1, queryParams);
        removeQueryParams(parts2, queryParams);
        // Fragment params should be before query params
        removeFragmentParams(parts1, fragmentParams);
        removeFragmentParams(parts2, fragmentParams);
        if (parts1.isEmpty()) {
            return CollectionUtils.concat(parts2, CollectionUtils.concat(fragmentParams, queryParams));
        }
        if (parts2.isEmpty()) {
            return CollectionUtils.concat(parts1, CollectionUtils.concat(fragmentParams, queryParams));
        }

        if (parts2.get(0) instanceof UriTemplateParser.Expression expression
            && expression.type() == UriTemplateParser.ExpressionType.NONE) {
            parts2.add(0, new UriTemplateParser.Literal("/"));
        }
        List<UriTemplateParser.Part> concat = new ArrayList<>(parts1.size() + parts2.size());
        UriTemplateParser.Part parts1Last = parts1.get(parts1.size() - 1);
        UriTemplateParser.Part parts2First = parts2.get(0);
        concat.addAll(parts1.subList(0, parts1.size() - 1));
        if (parts1Last instanceof UriTemplateParser.Literal literalPart1) {
            String literal1 = literalPart1.text();
            boolean literal1EndsWithSlash = literal1.endsWith("/");
            if (literal1EndsWithSlash
                && parts2First instanceof UriTemplateParser.Expression expression
                && expression.type() == UriTemplateParser.ExpressionType.PATH_SEGMENT_EXPANSION) {
                // Strip / from the last part of parts1, because it's needed for the new part
                parts1Last = new UriTemplateParser.Literal(literal1.substring(0, literal1.length() - 1));
            }
            if (parts2First instanceof UriTemplateParser.Literal literalPart2) {
                String literal2 = literalPart2.text();
                boolean literal2StartsWithSlash = literal2.startsWith("/");
                if (literal1EndsWithSlash && literal2StartsWithSlash) {
                    literal1 = literal1.substring(0, literal1.length() - 1);
                } else if (!literal1EndsWithSlash && !literal2StartsWithSlash) {
                    literal1 += "/";
                } else if (literal2.equals("/") && parts2.size() == 1 && queryParams.isEmpty() && fragmentParams.isEmpty()) {
                    literal2 = "";
                }
                parts1Last = new UriTemplateParser.Literal(literal1.concat(literal2));
                parts2First = null;
            }
        }
        concat.add(parts1Last);
        if (parts2First != null) {
            concat.add(parts2First);
        }
        concat.addAll(parts2.subList(1, parts2.size()));
        concat.addAll(fragmentParams);
        concat.addAll(queryParams);
        return concat;
    }

    private static void removeQueryParams(List<UriTemplateParser.Part> parts1, List<UriTemplateParser.Part> params) {
        for (Iterator<Part> iterator = parts1.iterator(); iterator.hasNext(); ) {
            UriTemplateParser.Part part = iterator.next();
            if (part instanceof UriTemplateParser.Expression expression && expression.type().isQueryPart()) {
                params.add(expression);
                iterator.remove();
            }
        }
    }

    private static void removeFragmentParams(List<UriTemplateParser.Part> parts1, List<UriTemplateParser.Part> params) {
        for (Iterator<UriTemplateParser.Part> iterator = parts1.iterator(); iterator.hasNext(); ) {
            UriTemplateParser.Part part = iterator.next();
            if (part instanceof UriTemplateParser.Expression expression && expression.type() == UriTemplateParser.ExpressionType.FRAGMENT_EXPANSION) {
                params.add(expression);
                iterator.remove();
            }
        }
    }

    private static boolean isAllowedCharacter(char c) {
        return switch (c) {
            case '<', '>', '\\', '^', '`', '{', '|', '}' -> false;
            default -> true;
        };
    }

    private static Expression parseExpression(char[] input, int fromIndex, int toIndex) {
        List<Variable> variables = new ArrayList<>(2);
        ExpressionType expressionType = parseOperator(input[fromIndex]);
        int variableNameStartIndex = expressionType == ExpressionType.NONE ? fromIndex : fromIndex + 1;
        int variableNameEndIndex = -1;
        int variableModifierStartIndex = -1;
        int variableModifierEndIndex = -1;
        for (int i = variableNameStartIndex; i < toIndex; i++) {
            char c = input[i];
            if (c == ',') {
                if (variableModifierStartIndex != -1) {
                    variableModifierEndIndex = i;
                } else {
                    variableNameEndIndex = i;
                }
                variables.add(createVariable(
                    input,
                    variableNameStartIndex,
                    variableNameEndIndex,
                    variableModifierStartIndex,
                    variableModifierEndIndex
                ));
                variableNameStartIndex = i + 1;
                variableNameEndIndex = -1;
                variableModifierStartIndex = -1;
                variableModifierEndIndex = -1;
            } else if (c == ':') {
                variableNameEndIndex = i;
                variableModifierStartIndex = i + 1;
            } else {
                if (variableModifierStartIndex != -1) {
                    variableModifierEndIndex = i;
                } else {
                    variableNameEndIndex = i;
                }
            }
        }
        if (variableModifierStartIndex != -1) {
            variableModifierEndIndex = toIndex;
        } else {
            variableNameEndIndex = toIndex;
        }
        variables.add(createVariable(
            input,
            variableNameStartIndex,
            variableNameEndIndex,
            variableModifierStartIndex,
            variableModifierEndIndex
        ));
        return new Expression(expressionType, variables);
    }

    private static Variable createVariable(char[] input,
                                           int variableNameStartIndex,
                                           int variableNameEndIndex,
                                           int variableModifierStartIndex,
                                           int variableModifierEndIndex) {
        boolean explore = false;
        if (input[variableNameEndIndex - 1] == '*') {
            explore = true;
            variableNameEndIndex--;
        }
        String modifier = null;
        if (variableModifierStartIndex != -1) {
            modifier = new String(input, variableModifierStartIndex, variableModifierEndIndex - variableModifierStartIndex);
        }
        return new Variable(
            new String(input, variableNameStartIndex, variableNameEndIndex - variableNameStartIndex),
            modifier,
            explore
        );
    }

    private static ExpressionType parseOperator(char c) {
        return switch (c) {
            case '+' -> ExpressionType.RESERVED_EXPANSION;
            case '#' -> ExpressionType.FRAGMENT_EXPANSION;
            case '.' -> ExpressionType.LABEL_EXPANSION;
            case '/' -> ExpressionType.PATH_SEGMENT_EXPANSION;
            case ';' -> ExpressionType.PATH_STYLE_PARAMETER_EXPANSION;
            case '?' -> ExpressionType.FORM_STYLE_PARAMETER_EXPANSION;
            case '&' -> ExpressionType.FORM_STYLE_QUERY_CONTINUATION;
            default -> ExpressionType.NONE;
        };
    }

    /**
     * The variable.
     *
     * @param name     The name
     * @param modifier The modifier
     * @param explode  Is exploded
     */
    public record Variable(String name, String modifier, boolean explode) {
    }

    /**
     * The expression part.
     *
     * @param type      The type
     * @param variables The variables
     */
    public record Expression(ExpressionType type, List<Variable> variables) implements Part {
        @Override
        public void visit(PartVisitor visitor) {
            visitor.visitExpression(type, variables);
        }
    }

    /**
     * The literal part.
     *
     * @param text The text
     */
    public record Literal(String text) implements Part {
        @Override
        public void visit(PartVisitor visitor) {
            visitor.visitLiteral(text);
        }
    }

    /**
     * The interface representing a template part.
     */
    public interface Part {

        /**
         * Visit parts using a visitor.
         *
         * @param visitor The visitor
         */
        void visit(PartVisitor visitor);

    }

    /**
     * The parts visitor.
     */
    public interface PartVisitor {

        /**
         * Visits a literal.
         *
         * @param literal The literal value
         */
        void visitLiteral(String literal);

        /**
         * Visits and expression.
         *
         * @param type      The type
         * @param variables The variables
         */
        void visitExpression(ExpressionType type, List<Variable> variables);

    }

    /**
     * The expression type.
     */
    public enum ExpressionType {
        NONE('0', ',', false, true),
        RESERVED_EXPANSION('+', ',', false, false),
        FRAGMENT_EXPANSION('#', ',', false, false),
        LABEL_EXPANSION('.', '.', false, true),
        PATH_SEGMENT_EXPANSION('/', '/', false, true),
        PATH_STYLE_PARAMETER_EXPANSION(';', ';', true, true),
        FORM_STYLE_PARAMETER_EXPANSION('?', '&', true, true),
        FORM_STYLE_QUERY_CONTINUATION('&', '&', true, true);

        private final char separator;
        private final char operator;
        private final boolean isQueryPart;
        private final boolean encode;

        ExpressionType(char operator, char separator, boolean isQueryPart, boolean encode) {
            this.separator = separator;
            this.operator = operator;
            this.isQueryPart = isQueryPart;
            this.encode = encode;
        }

        /**
         * @return Teh query part
         */
        public boolean isQueryPart() {
            return isQueryPart;
        }

        /**
         * @return The operator
         */
        public char getOperator() {
            return operator;
        }

        /**
         * @return The separator
         */
        public char getSeparator() {
            return separator;
        }

        /**
         * @return Is encoded
         */
        public boolean isEncode() {
            return encode;
        }
    }

}
