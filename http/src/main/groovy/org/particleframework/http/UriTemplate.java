/*
 * Copyright 2017 original authors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package org.particleframework.http;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>A Fast Implementation of URI Template specification. See https://tools.ietf.org/html/rfc6570 and https://medialize.github.io/URI.js/uri-template.html</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class UriTemplate {

    private static final String STRING_PATTERN_SCHEME = "([^:/?#]+):";

    private static final String STRING_PATTERN_USER_INFO = "([^@\\[/?#]*)";

    private static final String STRING_PATTERN_HOST_IPV4 = "[^\\[{/?#:]*";

    private static final String STRING_PATTERN_HOST_IPV6 = "\\[[\\p{XDigit}\\:\\.]*[%\\p{Alnum}]*\\]";

    private static final String STRING_PATTERN_HOST = "(" + STRING_PATTERN_HOST_IPV6 + "|" + STRING_PATTERN_HOST_IPV4 + ")";

    private static final String STRING_PATTERN_PORT = "(\\d*(?:\\{[^/]+?\\})?)";

    private static final String STRING_PATTERN_PATH = "([^#]*)";

    private static final String STRING_PATTERN_QUERY = "([^#]*)";

    private static final String STRING_PATTERN_REMAINING = "(.*)";

    // Regex patterns that matches URIs. See RFC 3986, appendix B
    private static final Pattern PATTERN_SCHEME = Pattern.compile("^" + STRING_PATTERN_SCHEME + "//.*");
    private static final Pattern PATTERN_FULL_URI = Pattern.compile(
            "^(" + STRING_PATTERN_SCHEME + ")?" + "(//(" + STRING_PATTERN_USER_INFO + "@)?" + STRING_PATTERN_HOST + "(:" + STRING_PATTERN_PORT +
                    ")?" + ")?" + STRING_PATTERN_PATH + "(\\?" + STRING_PATTERN_QUERY + ")?" + "(#" + STRING_PATTERN_REMAINING + ")?");


    private final String templateString;
    private final List<PathSegment> segments = new ArrayList<>();

    /**
     * Construct a new URI template for the given template
     *
     * @param templateString The template string
     */
    public UriTemplate(CharSequence templateString) {
        if (templateString == null) {
            throw new IllegalArgumentException("Argument [templateString] should not be null");
        }
        if (PATTERN_SCHEME.matcher(templateString).matches()) {
            Matcher matcher = PATTERN_FULL_URI.matcher(templateString);

            if (matcher.find()) {
                this.templateString = templateString.toString();
                String scheme = matcher.group(2);
                if (scheme != null) {
                    segments.add((parameters, previousHasContent) -> scheme + "://");
                }
                String userInfo = matcher.group(5);
                String host = matcher.group(6);
                String port = matcher.group(8);
                String path = matcher.group(9);
                String query = matcher.group(11);
                String fragment = matcher.group(13);
                if (userInfo != null) {
                    createParser(userInfo).parse(segments);
                }
                if (host != null) {
                    createParser(host).parse(segments);
                }
                if (port != null) {
                    createParser(':' + port).parse(segments);
                }
                if (path != null) {

                    if (fragment != null) {
                        createParser(path + '#' + fragment).parse(segments);
                    } else {
                        createParser(path).parse(segments);
                    }
                }
                if (query != null) {
                    createParser(query).parse(segments);
                }
            } else {
                throw new IllegalArgumentException("Invalid URI template: " + templateString);
            }
        } else {
            this.templateString = templateString.toString();
            createParser(this.templateString).parse(segments);
        }
    }

    /**
     * Expand the string with the given parameters
     *
     * @param parameters The parameters
     * @return The expanded URI
     */
    public String expand(Map<String, Object> parameters) {
        StringBuilder builder = new StringBuilder();
        if (segments != null) {
            boolean previousHasContent = false;
            for (PathSegment segment : segments) {
                String result = segment.expand(parameters, previousHasContent);
                if (result == null) break;
                previousHasContent = result.length() > 0;
                builder.append(result);
            }

        }
        return builder.toString();
    }

    @Override
    public String toString() {
        return templateString;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UriTemplate that = (UriTemplate) o;

        return templateString.equals(that.templateString);
    }

    @Override
    public int hashCode() {
        return templateString.hashCode();
    }

    /**
     * Creates a parser
     *
     * @param templateString
     * @return The created parser
     */
    protected UrlTemplateParser createParser(String templateString) {
        return new UrlTemplateParser(templateString);
    }

    /**
     * Represents an expandable path segment
     */
    protected interface PathSegment {
        String expand(Map<String, Object> parameters, boolean previousHasContent);
    }

    protected static class UrlTemplateParser {
        private static final int STATE_TEXT = 0; // raw text
        private static final int STATE_VAR_START = 1; // the start of a URI variable ie. {
        private static final int STATE_VAR_CONTENT = 2; // within a URI variable. ie. {var}
        private static final int STATE_VAR_NEXT = 11; // within the next variable in a URI variable declaration ie. {var, var2}
        private static final int STATE_VAR_MODIFIER = 12; // within a variable modifier ie. {var:1}
        private static final int STATE_VAR_NEXT_MODIFIER = 13; // within a variable modifier of a next variable ie. {var, var2:1}
        String templateText;
        private int state = STATE_TEXT;
        private char operator = '0';
        private char modifier = '0';
        private String varDelimiter;

        UrlTemplateParser(String templateText) {
            this.templateText = templateText;
        }

        void parse(List<PathSegment> segments) {
            char[] chars = templateText.toCharArray();
            StringBuilder buff = new StringBuilder();
            StringBuilder modBuff = new StringBuilder();
            int varCount = 0;
            for (char c : chars) {
                switch (state) {
                    case STATE_TEXT:
                        if (c == '{') {
                            if (buff.length() > 0) {
                                String val = buff.toString();
                                addRawContentSegment(segments, val);
                            }
                            buff.delete(0, buff.length());
                            state = STATE_VAR_START;
                            continue;
                        }
                        else {
                            buff.append(c);
                            continue;
                        }
                    case STATE_VAR_MODIFIER:
                    case STATE_VAR_NEXT_MODIFIER:
                        if (c == ' ') continue;
                    case STATE_VAR_NEXT:
                    case STATE_VAR_CONTENT:
                        switch (c) {
                            case ':':
                            case '*': // arrived to expansion modifier
                                modifier = c;
                                state = state == STATE_VAR_NEXT ? STATE_VAR_NEXT_MODIFIER : STATE_VAR_MODIFIER;
                                continue;
                            case ',': // arrived to new variable
                                state = STATE_VAR_NEXT;
                            case '}': // arrived to variable end

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
                                        case '#':
                                            encode = false;
                                            repeatPrefix = varCount < 1;
                                            prefix = String.valueOf(operator);
                                            delimiter = ",";
                                            break;
                                        case '.':
                                            encode = true;
                                            repeatPrefix = varCount < 1;
                                            prefix = String.valueOf(operator);
                                            delimiter = modifier == '*' ? prefix : ",";
                                            break;
                                        case '/':
                                            encode = true;
                                            repeatPrefix = varCount < 1;
                                            prefix = String.valueOf(operator);
                                            delimiter = modifier == '*' ? prefix : ",";
                                            break;
                                        case ';':
                                            encode = true;
                                            repeatPrefix = true;
                                            prefix = String.valueOf(operator) + val + '=';
                                            delimiter = modifier == '*' ? prefix : ",";
                                            break;
                                        case '?':
                                        case '&':
                                            encode = true;
                                            repeatPrefix = true;
                                            prefix = varCount < 1 ? String.valueOf(operator) + val + '=' : val + "=";
                                            delimiter = modifier == '*' ? '&' + val + '=' : ",";
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
                                    addVariableSegment(segments, val, prefix, delimiter, encode, repeatPrefix, modifierStr, modifierChar, operator, previous);
                                }
                                boolean hasAnotherVar = state == STATE_VAR_NEXT && c != '}';
                                if (hasAnotherVar) {
                                    String delimiter;
                                    switch (operator) {
                                        case ';':
                                            delimiter = null;
                                            break;
                                        case '?':
                                        case '&':
                                            delimiter = "&";
                                            break;
                                        case '.':
                                        case '/':
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
                                modifier = '0';

                                continue;
                            default:
                                switch (modifier) {
                                    case '*':
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
                            case '+':
                            case '#':
                            case '.':
                            case '/':
                            case ';':
                            case '?':
                            case '&':
                                operator = c;
                                state = STATE_VAR_CONTENT;
                                continue;
                            default:
                                state = STATE_VAR_CONTENT;
                                buff.append(c);
                        }
                }
            }

            if (state == STATE_TEXT && buff.length() > 0) {
                String val = buff.toString();
                addRawContentSegment(segments, val);
            }
        }

        /**
         * Adds a raw content segment
         *
         * @param segments The segments
         * @param value The value
         */
        protected void addRawContentSegment(List<PathSegment> segments, String value) {
            segments.add((parameters, previousHasContent) -> value);
        }

        /**
         * Adds a new variable segment
         *  @param segments The segments to augment
         * @param variable The variable
         * @param prefix The prefix to use when expanding the variable
         * @param delimiter The delimiter to use when expanding the variable
         * @param encode Whether to URL encode the variable
         * @param repeatPrefix Whether to repeat the prefix for each expanded variable
         * @param modifierStr The modifier string
         * @param modifierChar The modifier as char
         * @param operator The currently active operator
         * @param previousDelimiter The delimiter to use if a variable appeared before this variable
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
                                          String previousDelimiter) {
            segments.add((parameters, previousHasContent) -> {
                Object found = parameters.get(variable);
                if (found != null) {
                    String prefixToUse = prefix;
                    String result;
                    if (found.getClass().isArray()) {
                        found = Arrays.asList((Object[]) found);
                    }
                    boolean isQuery = this.operator == '?';
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
                        if (map.isEmpty()) return "";
                        final StringJoiner joiner;
                        if(modifierChar == '*') {

                            switch (this.operator) {
                                case '&':
                                case '?':
                                    prefixToUse = String.valueOf(this.operator);
                                    joiner = new StringJoiner(String.valueOf('&'));
                                break;
                                case ';':
                                    prefixToUse = String.valueOf(this.operator);
                                    joiner = new StringJoiner(String.valueOf(prefixToUse));
                                    break;
                                default:
                                    joiner = new StringJoiner(delimiter);
                            }
                        }
                        else {
                            joiner = new StringJoiner(delimiter);
                        }

                        map.forEach((key, value) -> {
                            String ks = key.toString();
                            String vs = value == null ? "" : value.toString();
                            String ek = encode ? encode(ks, isQuery) : escape(ks);
                            String ev = encode ? encode(vs, isQuery) : escape(vs);
                            if (modifierChar == '*') {
                                String finalValue = ek + '=' + ev;
                                joiner.add(finalValue);

                            } else {
                                joiner.add(ek);
                                joiner.add(ev);
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
                        switch (this.operator) {
                            case '/':
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
                    switch (this.operator) {
                        case '/':
                            return null;
                        default:
                            return "";
                    }
                }
            });
        }

        private String escape(String v) {
            return v.replaceAll("%", "%25").replaceAll("\\s", "%20");
        }

        private String applyModifier(String modifierStr, char modifierChar, String result, int len) {
            if (modifierChar == ':' && modifierStr.length() > 0) {
                if (Character.isDigit(modifierStr.charAt(0))) {
                    try {
                        Integer subResult = Integer.valueOf(modifierStr.trim());
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
                if(query) {
                    return encoded;
                }
                else {
                    return encoded.replaceAll("\\+", "%20");
                }
            } catch (UnsupportedEncodingException e) {
                throw new IllegalStateException("No available encoding", e);
            }
        }
    }
}
