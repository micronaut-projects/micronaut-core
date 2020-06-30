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

import io.micronaut.core.beans.BeanMap;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.StringUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>A Fast Implementation of URI Template specification. See https://tools.ietf.org/html/rfc6570 and
 * https://medialize.github.io/URI.js/uri-template.html.</p>
 * <p>
 * <p>Note: this class has a natural ordering that is inconsistent with equals.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class UriTemplate implements Comparable<UriTemplate> {

    private static final String STRING_PATTERN_SCHEME = "([^:/?#]+):";
    private static final String STRING_PATTERN_USER_INFO = "([^@\\[/?#]*)";
    private static final String STRING_PATTERN_HOST_IPV4 = "[^\\[{/?#:]*";
    private static final String STRING_PATTERN_HOST_IPV6 = "\\[[\\p{XDigit}\\:\\.]*[%\\p{Alnum}]*\\]";
    private static final String STRING_PATTERN_HOST = "(" + STRING_PATTERN_HOST_IPV6 + "|" + STRING_PATTERN_HOST_IPV4 + ")";
    private static final String STRING_PATTERN_PORT = "(\\d*(?:\\{[^/]+?\\})?)";
    private static final String STRING_PATTERN_PATH = "([^#]*)";
    private static final String STRING_PATTERN_QUERY = "([^#]*)";
    private static final String STRING_PATTERN_REMAINING = "(.*)";
    private static final char QUERY_OPERATOR = '?';
    private static final char SLASH_OPERATOR = '/';
    private static final char HASH_OPERATOR = '#';
    private static final char EXPAND_MODIFIER = '*';
    private static final char OPERATOR_NONE = '0';
    private static final char VAR_START = '{';
    private static final char VAR_END = '}';
    private static final char AND_OPERATOR = '&';
    private static final String SLASH_STRING = "/";
    private static final char DOT_OPERATOR = '.';

    // Regex patterns that matches URIs. See RFC 3986, appendix B
    static final Pattern PATTERN_SCHEME = Pattern.compile("^" + STRING_PATTERN_SCHEME + "//.*");
    static final Pattern PATTERN_FULL_PATH = Pattern.compile("^([^#\\?]*)(\\?([^#]*))?(\\#(.*))?$");
    static final Pattern PATTERN_FULL_URI = Pattern.compile(
            "^(" + STRING_PATTERN_SCHEME + ")?" + "(//(" + STRING_PATTERN_USER_INFO + "@)?" + STRING_PATTERN_HOST + "(:" + STRING_PATTERN_PORT +
                    ")?" + ")?" + STRING_PATTERN_PATH + "(\\?" + STRING_PATTERN_QUERY + ")?" + "(#" + STRING_PATTERN_REMAINING + ")?");

    private final String templateString;
    private final List<PathSegment> segments = new ArrayList<>();

    /**
     * Construct a new URI template for the given template.
     *
     * @param templateString The template string
     */
    public UriTemplate(CharSequence templateString) {
        this(templateString, new Object[0]);
    }

    /**
     * Construct a new URI template for the given template.
     *
     * @param templateString  The template string
     * @param parserArguments The parsed arguments
     */
    @SuppressWarnings("MagicNumber")
    protected UriTemplate(CharSequence templateString, Object... parserArguments) {
        if (templateString == null) {
            throw new IllegalArgumentException("Argument [templateString] should not be null");
        }

        String templateAsString = templateString.toString();
        if (templateAsString.endsWith(SLASH_STRING)) {
            int len = templateAsString.length();
            if (len > 1) {
                templateAsString = templateAsString.substring(0, len - 1);
            }
        }

        if (PATTERN_SCHEME.matcher(templateAsString).matches()) {
            Matcher matcher = PATTERN_FULL_URI.matcher(templateAsString);

            if (matcher.find()) {
                this.templateString = templateAsString;
                String scheme = matcher.group(2);
                if (scheme != null) {
                    segments.add(new UriTemplateParser.RawPathSegment(false, scheme + "://"));
                }
                String userInfo = matcher.group(5);
                String host = matcher.group(6);
                String port = matcher.group(8);
                String path = matcher.group(9);
                String query = matcher.group(11);
                String fragment = matcher.group(13);
                if (userInfo != null) {
                    createParser(userInfo, parserArguments).parse(segments);
                }
                if (host != null) {
                    createParser(host, parserArguments).parse(segments);
                }
                if (port != null) {
                    createParser(':' + port, parserArguments).parse(segments);
                }
                if (path != null) {

                    if (fragment != null) {
                        createParser(path + HASH_OPERATOR + fragment).parse(segments);
                    } else {
                        createParser(path, parserArguments).parse(segments);
                    }
                }
                if (query != null) {
                    createParser(query, parserArguments).parse(segments);
                }
            } else {
                throw new IllegalArgumentException("Invalid URI template: " + templateString);
            }
        } else {
            this.templateString = templateAsString;
            createParser(this.templateString, parserArguments).parse(segments);
        }
    }

    /**
     * @param templateString The template
     * @param segments       The list of segments
     */
    protected UriTemplate(String templateString, List<PathSegment> segments) {
        this.templateString = templateString;
        this.segments.addAll(segments);
    }

    /**
     * @return The number of segments that are variable
     */
    public long getVariableSegmentCount() {
        return segments.stream().filter(PathSegment::isVariable).count();
    }

    /**
     * @return The number of path segments that are variable
     */
    public long getPathVariableSegmentCount() {
        return segments.stream().filter(PathSegment::isVariable).filter(s -> !s.isQuerySegment()).count();
    }

    /**
     * @return The number of segments that are raw
     */
    public long getRawSegmentCount() {
        return segments.stream().filter(segment -> !segment.isVariable()).count();
    }

    /**
     * @return The number of segments that are raw
     */
    public int getRawSegmentLength() {
        return segments.stream()
                .filter(segment -> !segment.isVariable())
                .map(CharSequence::length)
                .reduce(Integer::sum)
                .orElse(0);
    }

    /**
     * Nests another URI template with this template.
     *
     * @param uriTemplate The URI template. If it does not begin with forward slash it will automatically be appended with forward slash
     * @return The new URI template
     */
    public UriTemplate nest(CharSequence uriTemplate) {
        return nest(uriTemplate, new Object[0]);
    }

    /**
     * Expand the string with the given parameters.
     *
     * @param parameters The parameters
     * @return The expanded URI
     */
    public String expand(Map<String, Object> parameters) {
        StringBuilder builder = new StringBuilder(templateString.length());
        boolean anyPreviousHasContent = false;
        boolean anyPreviousHasOperator = false;
        boolean queryParameter = false;
        for (PathSegment segment : segments) {
            String result = segment.expand(parameters, anyPreviousHasContent, anyPreviousHasOperator);
            if (result == null) {
                continue;
            }
            if (segment instanceof UriTemplateParser.VariablePathSegment) {
                UriTemplateParser.VariablePathSegment varPathSegment = (UriTemplateParser.VariablePathSegment) segment;
                if (varPathSegment.isQuerySegment && ! queryParameter) {
                    // reset anyPrevious* when we reach query parameters
                    queryParameter = true;
                    anyPreviousHasContent = false;
                    anyPreviousHasOperator = false;
                }
                final char operator = varPathSegment.getOperator();
                if (operator != OPERATOR_NONE && result.contains(String.valueOf(operator))) {
                    anyPreviousHasOperator = true;
                }
                anyPreviousHasContent = anyPreviousHasContent || result.length() > 0;
            }
            builder.append(result);
        }

        return builder.toString();
    }

    /**
     * Expand the string with the given bean.
     *
     * @param bean The bean
     * @return The expanded URI
     */
    public String expand(Object bean) {
        return expand(BeanMap.of(bean));
    }

    @Override
    public String toString() {
        return toString(pathSegment -> true);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        UriTemplate that = (UriTemplate) o;

        return templateString.equals(that.templateString);
    }

    @Override
    public int hashCode() {
        return templateString.hashCode();
    }

    @Override
    public int compareTo(UriTemplate o) {
        if (this == o) {
            return 0;
        }

        Integer thisVariableCount = 0;
        Integer thatVariableCount = 0;
        Integer thisRawLength = 0;
        Integer thatRawLength = 0;

        for (PathSegment segment: this.segments) {
            if (segment.isVariable()) {
                if (!segment.isQuerySegment()) {
                    thisVariableCount++;
                }
            } else {
                thisRawLength += segment.length();
            }
        }

        for (PathSegment segment: o.segments) {
            if (segment.isVariable()) {
                if (!segment.isQuerySegment()) {
                    thatVariableCount++;
                }
            } else {
                thatRawLength += segment.length();
            }
        }

        //using that.compareTo because more raw length should have higher precedence
        int rawCompare = thatRawLength.compareTo(thisRawLength);
        if (rawCompare == 0) {
            return thisVariableCount.compareTo(thatVariableCount);
        } else {
            return rawCompare;
        }
    }

    /**
     * Create a new {@link UriTemplate} for the given URI.
     *
     * @param uri The URI
     * @return The template
     */
    public static UriTemplate of(String uri) {
        return new UriTemplate(uri);
    }

    /**
     * Nests another URI template with this template.
     *
     * @param uriTemplate     The URI template. If it does not begin with forward slash it will automatically be
     *                        appended with forward slash
     * @param parserArguments The parsed arguments
     * @return The new URI template
     */
    protected UriTemplate nest(CharSequence uriTemplate, Object... parserArguments) {
        if (uriTemplate == null) {
            return this;
        }
        int len = uriTemplate.length();
        if (len == 0) {
            return this;
        }

        List<PathSegment> newSegments = buildNestedSegments(uriTemplate, len, parserArguments);
        return newUriTemplate(uriTemplate, newSegments);
    }

    /**
     * @param uriTemplate The URI template
     * @param newSegments The new segments
     * @return The new {@link UriTemplate}
     */
    protected UriTemplate newUriTemplate(CharSequence uriTemplate, List<PathSegment> newSegments) {
        return new UriTemplate(normalizeNested(this.templateString, uriTemplate), newSegments);
    }

    /**
     * Normalize a nested URI.
     * @param uri The URI
     * @param nested The nested URI
     * @return The new URI
     */
    protected String normalizeNested(String uri, CharSequence nested) {
        if (StringUtils.isEmpty(nested)) {
            return uri;
        }

        String nestedStr = nested.toString();
        char firstNested = nestedStr.charAt(0);
        int len = nestedStr.length();
        if (len == 1 && firstNested == SLASH_OPERATOR) {
            return uri;
        }

        switch (firstNested) {
            case VAR_START:
                if (len > 1) {
                    switch (nested.charAt(1)) {
                        case SLASH_OPERATOR:
                        case HASH_OPERATOR:
                        case QUERY_OPERATOR:
                        case AND_OPERATOR:
                            if (uri.endsWith(SLASH_STRING)) {
                                return uri.substring(0, uri.length() - 1) + nestedStr;
                            } else {
                                return uri + nestedStr;
                            }
                        default:
                            if (!uri.endsWith(SLASH_STRING)) {
                                return uri + SLASH_STRING + nestedStr;
                            } else {
                                return uri + nestedStr;
                            }
                    }
                } else {
                    return uri;
                }
            case SLASH_OPERATOR:
                if (uri.endsWith(SLASH_STRING)) {
                    return uri + nestedStr.substring(1);
                } else {
                    return uri + nestedStr;
                }
            default:
                if (uri.endsWith(SLASH_STRING)) {
                    return uri + nestedStr;
                } else {
                    return uri + SLASH_STRING + nestedStr;
                }
        }
    }

    /**
     * @param uriTemplate     The URI template
     * @param len             The lenght
     * @param parserArguments The parsed arguments
     * @return A list of path segments
     */
    protected List<PathSegment> buildNestedSegments(CharSequence uriTemplate, int len, Object... parserArguments) {
        List<PathSegment> newSegments = new ArrayList<>();
        List<PathSegment> querySegments = new ArrayList<>();

        for (PathSegment segment : segments) {
            if (!segment.isQuerySegment()) {
                newSegments.add(segment);
            } else {
                querySegments.add(segment);
            }
        }


        String templateString = uriTemplate.toString();
        if (shouldPrependSlash(templateString, len)) {
            templateString = SLASH_OPERATOR + templateString;
        } else if (!segments.isEmpty() && templateString.startsWith(SLASH_STRING)) {
            if (len == 1 && uriTemplate.charAt(0) == SLASH_OPERATOR) {
                templateString = "";
            } else {
                PathSegment last = segments.get(segments.size() - 1);
                if (last instanceof UriTemplateParser.RawPathSegment) {
                    String v = ((UriTemplateParser.RawPathSegment) last).value;
                    if (v.endsWith(SLASH_STRING)) {
                        templateString = templateString.substring(1);
                    } else {
                        templateString = normalizeNested(SLASH_STRING, templateString.substring(1));
                    }
                }
            }
        }
        createParser(templateString, parserArguments).parse(newSegments);
        newSegments.addAll(querySegments);
        return newSegments;
    }

    /**
     * Creates a parser.
     *
     * @param templateString  The template
     * @param parserArguments The parsed arguments
     * @return The created parser
     */
    protected UriTemplateParser createParser(String templateString, Object... parserArguments) {
        return new UriTemplateParser(templateString);
    }

    /**
     * Returns the template as a string filtering the segments
     * with the provided filter.
     *
     * @param filter The filter to test segments
     * @return The template as a string
     */
    protected String toString(Predicate<PathSegment> filter) {
        StringBuilder builder = new StringBuilder(templateString.length());
        UriTemplateParser.VariablePathSegment previousVariable = null;
        for (PathSegment segment : segments) {
            if (!filter.test(segment)) {
                continue;
            }
            boolean isVar = segment instanceof UriTemplateParser.VariablePathSegment;
            if (previousVariable != null && isVar) {
                UriTemplateParser.VariablePathSegment varSeg = (UriTemplateParser.VariablePathSegment) segment;
                if (varSeg.operator == previousVariable.operator && varSeg.modifierChar != EXPAND_MODIFIER) {
                    builder.append(varSeg.delimiter);
                } else {
                    builder.append(VAR_END);
                    builder.append(VAR_START);
                    char op = varSeg.operator;
                    if (OPERATOR_NONE != op) {
                        builder.append(op);
                    }
                }
                builder.append(segment.toString());
                previousVariable = varSeg;
            } else {
                if (isVar) {
                    previousVariable = (UriTemplateParser.VariablePathSegment) segment;
                    builder.append(VAR_START);
                    char op = previousVariable.operator;
                    if (OPERATOR_NONE != op) {
                        builder.append(op);
                    }
                    builder.append(segment.toString());
                } else {
                    if (previousVariable != null) {
                        builder.append(VAR_END);
                        previousVariable = null;
                    }
                    builder.append(segment.toString());
                }
            }
        }
        if (previousVariable != null) {
            builder.append(VAR_END);
        }
        return builder.toString();
    }

    private boolean shouldPrependSlash(String templateString, int len) {
        String parentString = this.templateString;
        int parentLen = parentString.length();
        return (parentLen > 0 && parentString.charAt(parentLen - 1) != SLASH_OPERATOR) &&
            templateString.charAt(0) != SLASH_OPERATOR &&
                isAdditionalPathVar(templateString, len);
    }

    private boolean isAdditionalPathVar(String templateString, int len) {
        if (len > 1) {
            boolean isVar = templateString.charAt(0) == VAR_START;
            if (isVar) {
                switch (templateString.charAt(1)) {
                    case SLASH_OPERATOR:
                    case QUERY_OPERATOR:
                    case HASH_OPERATOR:
                        return false;
                    default:
                        return true;
                }
            }
        }
        return templateString.charAt(0) != SLASH_OPERATOR;
    }

    /**
     * Represents an expandable path segment.
     */
    protected interface PathSegment extends CharSequence {

        /**
         * @return Whether this segment is part of the query string
         */
        default boolean isQuerySegment() {
            return false;
        }

        /**
         * If this path segment represents a variable returns the underlying variable name.
         *
         * @return The variable name if present
         */
        default Optional<String> getVariable() {
            return Optional.empty();
        }

        /**
         * @return True if this is a variable segment
         */
        default boolean isVariable() {
            return getVariable().isPresent();
        }

        /**
         * Expands the query segment.
         *
         * @param parameters         The parameters
         * @param previousHasContent Whether there was previous content
         * @param anyPreviousHasOperator Whether an operator is present
         * @return The expanded string
         */
        String expand(Map<String, Object> parameters, boolean previousHasContent, boolean anyPreviousHasOperator);
    }

    /**
     * An URI template parser.
     */
    protected static class UriTemplateParser {
        private static final int STATE_TEXT = 0; // raw text
        private static final int STATE_VAR_START = 1; // the start of a URI variable ie. {
        private static final int STATE_VAR_CONTENT = 2; // within a URI variable. ie. {var}
        private static final int STATE_VAR_NEXT = 11; // within the next variable in a URI variable declaration ie. {var, var2}
        private static final int STATE_VAR_MODIFIER = 12; // within a variable modifier ie. {var:1}
        private static final int STATE_VAR_NEXT_MODIFIER = 13; // within a variable modifier of a next variable ie. {var, var2:1}
        String templateText;
        private int state = STATE_TEXT;
        private char operator = OPERATOR_NONE; // zero means no operator
        private char modifier = OPERATOR_NONE; // zero means no modifier
        private String varDelimiter;
        private boolean isQuerySegment = false;

        /**
         * @param templateText The template
         */
        UriTemplateParser(String templateText) {
            this.templateText = templateText;
        }

        /**
         * Parse a list of segments.
         *
         * @param segments The list of segments
         */
        protected void parse(List<PathSegment> segments) {
            char[] chars = templateText.toCharArray();
            StringBuilder buff = new StringBuilder();
            StringBuilder modBuff = new StringBuilder();
            int varCount = 0;
            for (char c : chars) {
                switch (state) {
                    case STATE_TEXT:
                        if (c == VAR_START) {
                            if (buff.length() > 0) {
                                String val = buff.toString();
                                addRawContentSegment(segments, val, isQuerySegment);
                            }
                            buff.delete(0, buff.length());
                            state = STATE_VAR_START;
                            continue;
                        } else {
                            if (c == QUERY_OPERATOR || c == HASH_OPERATOR) {
                                isQuerySegment = true;
                            }
                            buff.append(c);
                            continue;
                        }
                    case STATE_VAR_MODIFIER:
                    case STATE_VAR_NEXT_MODIFIER:
                        if (c == ' ') {
                            continue;
                        }
                    case STATE_VAR_NEXT:
                    case STATE_VAR_CONTENT:
                        switch (c) {
                            case ':':
                            case EXPAND_MODIFIER: // arrived to expansion modifier
                                if (state == STATE_VAR_MODIFIER || state == STATE_VAR_NEXT_MODIFIER) {
                                    modBuff.append(c);
                                    continue;
                                }
                                modifier = c;
                                state = state == STATE_VAR_NEXT ? STATE_VAR_NEXT_MODIFIER : STATE_VAR_MODIFIER;
                                continue;
                            case ',': // arrived to new variable
                                state = STATE_VAR_NEXT;
                            case VAR_END: // arrived to variable end

                                if (buff.length() > 0) {
                                    String val = buff.toString();
                                    final String prefix;
                                    final String delimiter;
                                    final boolean encode;
                                    final boolean repeatPrefix;
                                    switch (operator) {
                                        case '+':
                                            encode = false;
                                            prefix = null;
                                            delimiter = ",";
                                            repeatPrefix = varCount < 1;
                                            break;
                                        case HASH_OPERATOR:
                                            encode = false;
                                            repeatPrefix = varCount < 1;
                                            prefix = String.valueOf(operator);
                                            delimiter = ",";
                                            break;
                                        case DOT_OPERATOR:
                                            encode = true;
                                            repeatPrefix = varCount < 1;
                                            prefix = String.valueOf(operator);
                                            delimiter = modifier == EXPAND_MODIFIER ? prefix : ",";
                                            break;
                                        case SLASH_OPERATOR:
                                            encode = true;
                                            repeatPrefix = varCount < 1;
                                            prefix = String.valueOf(operator);
                                            delimiter = modifier == EXPAND_MODIFIER ? prefix : ",";
                                            break;
                                        case ';':
                                            encode = true;
                                            repeatPrefix = true;
                                            prefix = String.valueOf(operator) + val + '=';
                                            delimiter = modifier == EXPAND_MODIFIER ? prefix : ",";
                                            break;
                                        case QUERY_OPERATOR:
                                        case AND_OPERATOR:
                                            encode = true;
                                            repeatPrefix = true;
                                            prefix = varCount < 1 ? String.valueOf(operator) + val + '=' : val + "=";
                                            delimiter = modifier == EXPAND_MODIFIER ? AND_OPERATOR + val + '=' : ",";
                                            break;
                                        default:
                                            repeatPrefix = varCount < 1;
                                            encode = true;
                                            prefix = null;
                                            delimiter = ",";
                                    }
                                    String modifierStr = modBuff.toString();
                                    char modifierChar = modifier;
                                    String previous = state == STATE_VAR_NEXT || state == STATE_VAR_NEXT_MODIFIER ? this.varDelimiter : null;
                                    addVariableSegment(segments, val, prefix, delimiter, encode, repeatPrefix, modifierStr, modifierChar, operator, previous, isQuerySegment);
                                }
                                boolean hasAnotherVar = state == STATE_VAR_NEXT && c != VAR_END;
                                if (hasAnotherVar) {
                                    String delimiter;
                                    switch (operator) {
                                        case ';':
                                            delimiter = null;
                                            break;
                                        case QUERY_OPERATOR:
                                        case AND_OPERATOR:
                                            delimiter = "&";
                                            break;
                                        case DOT_OPERATOR:
                                        case SLASH_OPERATOR:
                                            delimiter = String.valueOf(operator);
                                            break;
                                        default:
                                            delimiter = ",";
                                    }
                                    varDelimiter = delimiter;
                                    varCount++;
                                } else {
                                    varCount = 0;
                                }
                                state = hasAnotherVar ? STATE_VAR_NEXT : STATE_TEXT;
                                modBuff.delete(0, modBuff.length());
                                buff.delete(0, buff.length());
                                modifier = OPERATOR_NONE;
                                if (!hasAnotherVar) {
                                    operator = OPERATOR_NONE;
                                }
                                continue;
                            default:
                                switch (modifier) {
                                    case EXPAND_MODIFIER:
                                        throw new IllegalStateException("Expansion modifier * must be immediately followed by a closing brace '}'");
                                    case ':':
                                        modBuff.append(c);
                                        continue;
                                    default:
                                        buff.append(c);
                                        continue;
                                }

                        }
                    case STATE_VAR_START:
                        switch (c) {
                            case ' ':
                                continue;
                            case ';':
                            case QUERY_OPERATOR:
                            case AND_OPERATOR:
                            case HASH_OPERATOR:
                                isQuerySegment = true;
                            case '+':
                            case DOT_OPERATOR:
                            case SLASH_OPERATOR:
                                operator = c;
                                state = STATE_VAR_CONTENT;
                                continue;
                            default:
                                state = STATE_VAR_CONTENT;
                                buff.append(c);
                        }
                    default:
                        // no-op
                }
            }

            if (state == STATE_TEXT && buff.length() > 0) {
                String val = buff.toString();
                addRawContentSegment(segments, val, isQuerySegment);
            }
        }

        /**
         * Adds a raw content segment.
         *
         * @param segments       The segments
         * @param value          The value
         * @param isQuerySegment Whether is a query segment
         */
        protected void addRawContentSegment(List<PathSegment> segments, String value, boolean isQuerySegment) {
            segments.add(new RawPathSegment(isQuerySegment, value));
        }

        /**
         * Adds a new variable segment.
         *
         * @param segments          The segments to augment
         * @param variable          The variable
         * @param prefix            The prefix to use when expanding the variable
         * @param delimiter         The delimiter to use when expanding the variable
         * @param encode            Whether to URL encode the variable
         * @param repeatPrefix      Whether to repeat the prefix for each expanded variable
         * @param modifierStr       The modifier string
         * @param modifierChar      The modifier as char
         * @param operator          The currently active operator
         * @param previousDelimiter The delimiter to use if a variable appeared before this variable
         * @param isQuerySegment    Whether is a query segment
         */
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
            segments.add(new VariablePathSegment(isQuerySegment, variable, prefix, delimiter, encode, modifierChar, operator, modifierStr, previousDelimiter, repeatPrefix));
        }

        private String escape(String v) {
            return v.replace("%", "%25").replaceAll("\\s", "%20");
        }

        private String applyModifier(String modifierStr, char modifierChar, String result, int len) {
            if (modifierChar == ':' && modifierStr.length() > 0) {
                if (Character.isDigit(modifierStr.charAt(0))) {
                    try {
                        int subResult = Integer.parseInt(modifierStr.trim(), 10);
                        if (subResult < len) {
                            result = result.substring(0, subResult);
                        }
                    } catch (NumberFormatException e) {
                        result = ":" + modifierStr;
                    }

                }
            }
            return result;
        }

        private String encode(String str, boolean query) {
            try {
                String encoded = URLEncoder.encode(str, "UTF-8");
                if (query) {
                    return encoded;
                } else {
                    return encoded.replace("+", "%20");
                }
            } catch (UnsupportedEncodingException e) {
                throw new IllegalStateException("No available encoding", e);
            }
        }

        /**
         * Raw path segment implementation.
         */
        private static class RawPathSegment implements PathSegment {
            private final boolean isQuerySegment;
            private final String value;

            public RawPathSegment(boolean isQuerySegment, String value) {
                this.isQuerySegment = isQuerySegment;
                this.value = value;
            }

            @Override
            public boolean isQuerySegment() {
                return isQuerySegment;
            }

            @Override
            public String expand(Map<String, Object> parameters, boolean previousHasContent, boolean anyPreviousHasOperator) {
                return value;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) {
                    return true;
                }
                if (o == null || getClass() != o.getClass()) {
                    return false;
                }

                RawPathSegment that = (RawPathSegment) o;

                if (isQuerySegment != that.isQuerySegment) {
                    return false;
                }
                return value != null ? value.equals(that.value) : that.value == null;
            }

            @Override
            public int hashCode() {
                int result = (isQuerySegment ? 1 : 0);
                result = 31 * result + (value != null ? value.hashCode() : 0);
                return result;
            }

            @Override
            public int length() {
                return value.length();
            }

            @Override
            public char charAt(int index) {
                return value.charAt(index);
            }

            @Override
            public CharSequence subSequence(int start, int end) {
                return value.subSequence(start, end);
            }

            @Override
            public String toString() {
                return value;
            }
        }

        /**
         * Variable path segment implementation.
         */
        private class VariablePathSegment implements PathSegment {

            private final boolean isQuerySegment;
            private final String variable;
            private final String prefix;
            private final String delimiter;
            private final boolean encode;
            private final char modifierChar;
            private final char operator;
            private final String modifierStr;
            private final String previousDelimiter;
            private final boolean repeatPrefix;

            public VariablePathSegment(boolean isQuerySegment, String variable, String prefix, String delimiter, boolean encode, char modifierChar, char operator, String modifierStr, String previousDelimiter, boolean repeatPrefix) {
                this.isQuerySegment = isQuerySegment;
                this.variable = variable;
                this.prefix = prefix;
                this.delimiter = delimiter;
                this.encode = encode;
                this.modifierChar = modifierChar;
                this.operator = operator;
                this.modifierStr = modifierStr;
                this.previousDelimiter = previousDelimiter;
                this.repeatPrefix = repeatPrefix;
            }

            @Override
            public Optional<String> getVariable() {
                return Optional.of(variable);
            }

            public char getOperator() {
                return this.operator;
            }

            @Override
            public boolean isQuerySegment() {
                return isQuerySegment;
            }

            @Override
            public int length() {
                return toString().length();
            }

            @Override
            public char charAt(int index) {
                return toString().charAt(index);
            }

            @Override
            public CharSequence subSequence(int start, int end) {
                return toString().subSequence(start, end);
            }

            @Override
            public String toString() {
                StringBuilder builder = new StringBuilder();
                builder.append(variable);
                if (modifierChar != OPERATOR_NONE) {
                    builder.append(modifierChar);
                    if (null != modifierStr) {
                        builder.append(modifierStr);
                    }
                }
                return builder.toString();
            }

            @Override
            public String expand(Map<String, Object> parameters, boolean previousHasContent, boolean anyPreviousHasOperator) {
                Object found = parameters.get(variable);
                boolean isOptional = found instanceof Optional;
                if (found != null && !(isOptional && !((Optional) found).isPresent())) {
                    if (isOptional) {
                        found = ((Optional) found).get();
                    }
                    String prefixToUse = prefix;
                    if (operator == QUERY_OPERATOR && !anyPreviousHasOperator && prefix != null && !prefix.startsWith(String.valueOf(operator))) {
                        prefixToUse = operator + prefix;
                    }

                    String result;
                    if (found.getClass().isArray()) {
                        found = Arrays.asList((Object[]) found);
                    }
                    boolean isQuery = operator == QUERY_OPERATOR;
                    
                    if (modifierChar == EXPAND_MODIFIER) {
                        found = expandPOJO(found); // Turn POJO into a Map
                    }
                    
                    if (found instanceof Iterable) {
                        Iterable iter = ((Iterable) found);
                        if (iter instanceof Collection && ((Collection) iter).isEmpty()) {
                            return "";
                        }
                        StringJoiner joiner = new StringJoiner(delimiter);
                        for (Object o : iter) {
                            if (o != null) {
                                String v = o.toString();
                                joiner.add(encode ? encode(v, isQuery) : escape(v));
                            }
                        }
                        result = joiner.toString();
                    } else if (found instanceof Map) {
                        Map<Object, Object> map = (Map<Object, Object>) found;
                        map.values().removeIf(Objects::isNull);
                        if (map.isEmpty()) {
                            return "";
                        }
                        final StringJoiner joiner;
                        if (modifierChar == EXPAND_MODIFIER) {

                            switch (operator) {
                                case AND_OPERATOR:
                                case QUERY_OPERATOR:
                                    prefixToUse = String.valueOf(anyPreviousHasOperator ? AND_OPERATOR : operator);
                                    joiner = new StringJoiner(String.valueOf(AND_OPERATOR));
                                    break;
                                case ';':
                                    prefixToUse = String.valueOf(operator);
                                    joiner = new StringJoiner(String.valueOf(prefixToUse));
                                    break;
                                default:
                                    joiner = new StringJoiner(delimiter);
                            }
                        } else {
                            joiner = new StringJoiner(delimiter);
                        }

                        map.forEach((key, some) -> {
                            String ks = key.toString();
                            Iterable<?> values = (some instanceof Iterable) ? (Iterable) some : Collections.singletonList(some);
                            for (Object value: values) {
                                if (value == null) {
                                    continue;
                                }
                                String vs = value.toString();
                                String ek = encode ? encode(ks, isQuery) : escape(ks);
                                String ev = encode ? encode(vs, isQuery) : escape(vs);
                                if (modifierChar == EXPAND_MODIFIER) {
                                    String finalValue = ek + '=' + ev;
                                    joiner.add(finalValue);
                                } else {
                                    joiner.add(ek);
                                    joiner.add(ev);
                                }
                            }
                        });
                        result = joiner.toString();
                    } else {
                        String str = found.toString();
                        str = applyModifier(modifierStr, modifierChar, str, str.length());
                        result = encode ? encode(str, isQuery) : escape(str);
                    }
                    int len = result.length();
                    StringBuilder finalResult = new StringBuilder(previousHasContent && previousDelimiter != null ? previousDelimiter : "");
                    if (len == 0) {
                        switch (operator) {
                            case SLASH_OPERATOR:
                                break;
                            case ';':
                                if (prefixToUse != null && prefixToUse.endsWith("=")) {
                                    finalResult.append(prefixToUse.substring(0, prefixToUse.length() - 1)).append(result);
                                    break;
                                }
                            default:
                                if (prefixToUse != null) {
                                    finalResult.append(prefixToUse).append(result);
                                } else {
                                    finalResult.append(result);
                                }
                        }
                    } else if (prefixToUse != null && repeatPrefix) {
                        finalResult.append(prefixToUse).append(result);
                    } else {
                        finalResult.append(result);
                    }
                    return finalResult.toString();
                } else {
                    switch (operator) {
                        case SLASH_OPERATOR:
                            return null;
                        default:
                            return "";
                    }
                }


            }

            private Object expandPOJO(Object found) {
                // Check for common expanded types, such as list or Map
                if (found instanceof Iterable || found instanceof Map) {
                    return found;
                }
                // If a simple value, just use that
                if (found == null || ClassUtils.isJavaLangType(found.getClass())) {
                    return found;
                }
                // Otherwise, expand the object into properties (after all, the user asked for an expanded parameter)
                return BeanMap.of(found);
            }
        }
    }

}
