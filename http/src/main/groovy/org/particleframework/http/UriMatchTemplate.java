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

import java.net.URI;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extends {@link UriTemplate} and adds the ability to match a URI to a given template.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class UriMatchTemplate extends UriTemplate {

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

    /**
     * Match the given {@link URI} object
     *
     * @param uri The URI
     * @return True if it matches
     */
    public Optional<UriMatchInfo> match(URI uri) {
        return match(uri.toString());
    }

    /**
     * Match the given URI string
     *
     * @param uri The uRI
     * @return True if it matches
     */
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
    protected UrlTemplateParser createParser(String templateString) {
        this.pattern = new StringBuilder();
        this.variableList = new ArrayList<>();
        return new UrlMatchTemplateParser(templateString, this);
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

    protected static class UrlMatchTemplateParser extends UrlTemplateParser {
        private static final String VARIABLE_MATCH_PATTERN = "([^\\/\\?\\.]";

        final UriMatchTemplate matchTemplate;

        UrlMatchTemplateParser(String templateText, UriMatchTemplate matchTemplate) {
            super(templateText);
            this.matchTemplate = matchTemplate;
        }


        @Override
        protected void addRawContentSegment(List<PathSegment> segments, String value) {
            matchTemplate.pattern.append(Pattern.quote(value));
            super.addRawContentSegment(segments, value);
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
                                          String previousDelimiter) {
            matchTemplate.variableList.add(variable);
            StringBuilder pattern = matchTemplate.pattern;
            boolean hasMod = modifierChar == ':' && modifierStr.length() > 0;
            String operatorPrefix = "";
            String operatorQuantifier = "";
            String variableQuantifier = "+)";
            String variablePattern = VARIABLE_MATCH_PATTERN;
            if (hasMod) {
                char firstChar = modifierStr.charAt(0);
                if (firstChar == '?') {
                    operatorQuantifier = modifierStr;
                    variableQuantifier = variableQuantifier + modifierStr;
                }
                else if(Character.isDigit(firstChar)) {
                    variableQuantifier = "{1," + modifierStr + "})";
                }
                else {
                    if(modifierStr.endsWith("*") || modifierStr.endsWith("*?")) {
                        operatorQuantifier = "?";
                    }
                    operatorPrefix = "(";
                    variablePattern =  modifierStr + ")";
                    variableQuantifier = "";
                }
            }
            pattern.append(operatorPrefix);
            switch (operator) {
                case '.':
                case '/':
                    pattern.append("\\").append(String.valueOf(operator))
                            .append(operatorQuantifier);
                case '0': // no active operator
                    pattern.append(variablePattern)
                            .append(variableQuantifier);
                    break;
            }
            super.addVariableSegment(segments, variable, prefix, delimiter, encode, repeatPrefix, modifierStr, modifierChar, operator, previousDelimiter);
        }
    }
}
