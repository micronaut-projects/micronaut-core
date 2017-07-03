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
package org.particleframework.http.uri;

import java.net.URI;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extends {@link UriTemplate} and adds the ability to match a URI to a given template using the {@link #match(URI)} method.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class UriMatchTemplate extends UriTemplate implements UriMatcher {

    private StringBuilder pattern;
    private List<String> variableList;
    private final Pattern matchPattern;
    private final String[] variables;

    /**
     * Construct a new URI template for the given template
     *
     * @param templateString The template string
     */
    public UriMatchTemplate(CharSequence templateString) {
        super(templateString);

        this.matchPattern = Pattern.compile(pattern.toString());
        this.variables = variableList.toArray(new String[variableList.size()]);
        // cleanup / reduce memory consumption
        this.pattern = null;
        this.variableList = null;
    }

    protected UriMatchTemplate(CharSequence templateString, List<PathSegment> segments, Pattern matchPattern, String...variables ) {
        super(templateString, segments);
        this.matchPattern = matchPattern;
        this.variables = variables;
    }

    /**
     * Match the given URI string
     *
     * @param uri The uRI
     * @return True if it matches
     */
    @Override
    public Optional<UriMatchInfo> match(String uri) {
        Matcher matcher = matchPattern.matcher(uri);
        if (matcher.matches()) {
            if (variables.length == 0) {
                return Optional.of(new DefaultUriMatchInfo(uri, Collections.emptyMap()));
            } else {
                Map<String, Object> variableMap = new LinkedHashMap<>();
                int count = matcher.groupCount();
                for (int j = 0; j < variables.length; j++) {
                    int index = j + 1;
                    if (index > count) break;
                    String variable = variables[j];
                    String value = matcher.group(index);
                    variableMap.put(variable, value);
                }
                return Optional.of(new DefaultUriMatchInfo(uri, variableMap));
            }
        }
        return Optional.empty();
    }

    @Override
    public UriMatchTemplate nest(CharSequence uriTemplate) {
        return (UriMatchTemplate) super.nest(uriTemplate);
    }

    @Override
    protected UriTemplate newUriTemplate(CharSequence uriTemplate, List<PathSegment> newSegments) {
        Pattern newPattern = Pattern.compile(this.matchPattern.toString() + pattern.toString());
        List<String> newList = new ArrayList<>();
        newList.addAll(Arrays.asList(variables));
        newList.addAll(variableList);
        pattern = null;
        variableList = null;
        return new UriMatchTemplate(uriTemplate, newSegments, newPattern,newList.toArray(new String[newList.size()]));
    }

    @Override
    protected UriTemplateParser createParser(String templateString) {
        this.pattern = new StringBuilder();
        this.variableList = new ArrayList<>();
        return new UriMatchTemplateParser(templateString, this);
    }

    protected static class DefaultUriMatchInfo implements UriMatchInfo {
        private final String uri;
        private final Map<String, Object> variables;

        protected DefaultUriMatchInfo(String uri, Map<String, Object> variables) {
            this.uri = uri;
            this.variables = variables;
        }

        @Override
        public String getUri() {
            return uri;
        }

        @Override
        public Map<String, Object> getVariables() {
            return variables;
        }


        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            DefaultUriMatchInfo that = (DefaultUriMatchInfo) o;
            return uri.equals(that.uri) && variables.equals(that.variables);
        }

        @Override
        public int hashCode() {
            int result = uri.hashCode();
            result = 31 * result + variables.hashCode();
            return result;
        }
    }

    /**
     * <p>Extended version of {@link UriTemplateParser} that builds
     * a regular expression to match a path. Note that fragments (#) and queries (?) are ignored for the purposes of matching.</p>
     */
    protected static class UriMatchTemplateParser extends UriTemplateParser {
        private static final String VARIABLE_MATCH_PATTERN = "([^\\/\\?\\.#&;\\+]";

        final UriMatchTemplate matchTemplate;

        UriMatchTemplateParser(String templateText, UriMatchTemplate matchTemplate) {
            super(templateText);
            this.matchTemplate = matchTemplate;
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
            matchTemplate.variableList.add(variable);
            StringBuilder pattern = matchTemplate.pattern;
            int modLen = modifierStr.length();
            boolean hasModifier = modifierChar == ':' && modLen > 0;
            String operatorPrefix = "";
            String operatorQuantifier = "";
            String variableQuantifier = "+)";
            String variablePattern = VARIABLE_MATCH_PATTERN;
            if (hasModifier) {
                char firstChar = modifierStr.charAt(0);
                if (firstChar == '?') {
                    operatorQuantifier = modifierStr;
                    variableQuantifier = variableQuantifier + modifierStr;
                }
                else if(modifierStr.chars().allMatch(Character::isDigit)) {
                    variableQuantifier = "{1," + modifierStr + "})";
                }
                else {
                    char lastChar = modifierStr.charAt(modLen - 1);
                    if(lastChar == '*' ||
                            (modLen > 1 && lastChar == '?' && (modifierStr.charAt(modLen - 2) == '*' || modifierStr.charAt(modLen-2) == '+'))) {
                        operatorQuantifier = "?";
                    }
                    if(operator != '.') {

                        operatorPrefix = "(";
                        variablePattern =  modifierStr + ")";
                    }
                    else {
                        variablePattern =  "(" + modifierStr + ")";
                    }
                    variableQuantifier = "";
                }
            }
            pattern.append(operatorPrefix);
            switch (operator) {
                case '.':
                case '/':
                    pattern.append("\\").append(String.valueOf(operator))
                            .append(operatorQuantifier);
                case '+':
                case '0': // no active operator
                    pattern.append(variablePattern)
                            .append(variableQuantifier);
                    break;
            }
            super.addVariableSegment(segments, variable, prefix, delimiter, encode, repeatPrefix, modifierStr, modifierChar, operator, previousDelimiter, isQuerySegment);
        }
    }
}
