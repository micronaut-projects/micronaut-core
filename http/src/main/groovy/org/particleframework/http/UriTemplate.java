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
    private final String scheme;
    private final String userInfo;
    private final String host;
    private final String port;
    private final String path;
    private final String query;
    private final String fragment;
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
                this.scheme = matcher.group(2);
                if (scheme != null) {
                    segments.add((parameters, previousHasContent) -> scheme + "://");
                }
                this.userInfo = matcher.group(5);
                this.host = matcher.group(6);
                this.port = matcher.group(8);
                this.path = matcher.group(9);
                this.query = matcher.group(11);
                this.fragment = matcher.group(13);
                if (userInfo != null) {
                    new UrlTemplateParser(userInfo).parse(segments);
                }
                if (host != null) {
                    new UrlTemplateParser(host).parse(segments);
                }
                if (port != null) {
                    new UrlTemplateParser(':' + port).parse(segments);
                }
                if (path != null) {

                    if (fragment != null) {
                        new UrlTemplateParser(path + '#' + fragment).parse(segments);
                    } else {
                        new UrlTemplateParser(path).parse(segments);
                    }
                }
                if (query != null) {
                    new UrlTemplateParser(query).parse(segments);
                }
            } else {
                throw new IllegalArgumentException("Invalid URI template: " + templateString);
            }
        } else {
            scheme = null;
            userInfo = null;
            host = null;
            port = null;
            path = templateString.toString();
            query = null;
            fragment = null;
            new UrlTemplateParser(path).parse(segments);
        }
        this.templateString = templateString.toString();
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

    /**
     * Test whether the given URI matches this URI
     *
     * @param uri The URI
     * @return True it it does
     */
    public boolean matches(String uri) {
        throw new UnsupportedOperationException("Not Yet Implemented");
    }

    @Override
    public String toString() {
        return templateString;
    }

    private interface PathSegment {
        String expand(Map<String, Object> parameters, boolean previousHasContent);
    }

    private static class UrlTemplateParser {
        private static final int STATE_TEXT = 0;
        private static final int STATE_VAR_START = 1;
        private static final int STATE_VAR_CONTENT = 2;
        private static final int STATE_VAR_NEXT = 11;
        private static final int STATE_VAR_MODIFIER = 12;
        private static final int STATE_VAR_NEXT_MODIFIER = 13;
        String templateText;
        private int state = STATE_TEXT;
        private char operator = '0';
        private char modifier = '0';
        private String varDelimiter;

        public UrlTemplateParser(String templateText) {
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
                                segments.add((parameters, previousHasContent) -> val);
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
                                    segments.add((parameters, previousHasContent) -> {
                                        Object found = parameters.get(val);
                                        if (found != null) {
                                            String prefixToUse = prefix;
                                            String result;
                                            if (found.getClass().isArray()) {
                                                found = Arrays.asList((Object[]) found);
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
                                                        joiner.add(encode ? encode(v) : escape(v));
                                                    }
                                                }
                                                result = joiner.toString();
                                            } else if (found instanceof Map) {
                                                Map<Object, Object> map = (Map<Object, Object>) found;
                                                if (map.isEmpty()) return "";
                                                final StringJoiner joiner;
                                                if(modifierChar == '*') {

                                                    switch (operator) {
                                                        case '&':
                                                        case '?':
                                                            prefixToUse = String.valueOf(operator);
                                                            joiner = new StringJoiner(String.valueOf('&'));
                                                        break;
                                                        case ';':
                                                            prefixToUse = String.valueOf(operator);
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
                                                    String ek = encode ? encode(ks) : escape(ks);
                                                    String ev = encode ? encode(vs) : escape(vs);
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
                                                result = encode ? encode(str) : escape(str);
                                            }
                                            int len = result.length();
                                            StringBuilder finalResult = new StringBuilder(previousHasContent && previous != null ? previous : "");
                                            if (len == 0) {
                                                switch (operator) {
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
                                            switch (operator) {
                                                case '/':
                                                    return null;
                                                default:
                                                    return "";
                                            }
                                        }
                                    });
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
                segments.add((parameters, previousHasContent) -> val);
            }
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

        private String encode(String str) {
            try {
                String encode = URLEncoder.encode(str, "UTF-8");
                return encode.replaceAll("\\+", "%20");
            } catch (UnsupportedEncodingException e) {
                throw new IllegalStateException("No available encoding", e);
            }
        }
    }
}
