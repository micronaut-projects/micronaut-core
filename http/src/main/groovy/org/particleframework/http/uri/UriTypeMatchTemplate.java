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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * <p>A {@link UriMatchTemplate} that allows specifying types for the URI variables</p>
 *
 * @see UriMatchTemplate
 * @see org.particleframework.http.uri.UriTemplate
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class UriTypeMatchTemplate extends UriMatchTemplate {

    private Class[] variableTypes;

    public UriTypeMatchTemplate(CharSequence templateString, Class...variableTypes) {
        super(templateString, new Object[]{variableTypes});
        this.variableTypes = variableTypes == null ? new Class[0] : variableTypes;
    }

    protected UriTypeMatchTemplate(CharSequence templateString, List<PathSegment> segments, Pattern matchPattern, Class[] variableTypes, String... variables) {
        super(templateString, segments, matchPattern, variables);
        this.variableTypes = variableTypes;
    }

    @Override
    public UriTypeMatchTemplate nest(CharSequence uriTemplate) {
        return (UriTypeMatchTemplate) super.nest(uriTemplate);
    }

    public UriTypeMatchTemplate nest(CharSequence uriTemplate, Class...variableTypes) {
        return (UriTypeMatchTemplate) super.nest(uriTemplate, new Object[]{variableTypes});
    }

    @Override
    public String expand(Map<String, Object> parameters) {
        return super.expand(parameters);
    }

    @Override
    protected UriTemplateParser createParser(String templateString, Object... parserArguments) {
        this.pattern = new StringBuilder();
        this.variableList = new ArrayList<>();
        this.variableTypes = parserArguments != null && parserArguments.length > 0 ? (Class[])parserArguments[0] : new Class[0];
        return new TypedUriMatchTemplateParser(templateString, this);
    }

    @Override
    protected UriMatchTemplate newUriMatchTemplate(CharSequence uriTemplate, List<PathSegment> newSegments, Pattern newPattern, String[] variables) {
        return new UriTypeMatchTemplate(uriTemplate, newSegments, newPattern, variableTypes, variables);
    }

    protected String resolveTypePattern(Class variableType, String variable, char operator) {
        if(Number.class.isAssignableFrom(variableType)) {
            if(Double.class == variableType || Float.class == variableType || BigDecimal.class == variableType) {
                return "([\\d\\.+]";
            }
            else {
                return "([\\d+]";
            }
        }
        else {
            return VARIABLE_MATCH_PATTERN;
        }
    }

    protected static class TypedUriMatchTemplateParser extends UriMatchTemplateParser {

        private int variableIndex = 0;

        TypedUriMatchTemplateParser(String templateText, UriTypeMatchTemplate matchTemplate) {
            super(templateText, matchTemplate);
        }

        @Override
        public UriTypeMatchTemplate getMatchTemplate() {
            return (UriTypeMatchTemplate) super.getMatchTemplate();
        }

        @Override
        protected String getVariablePattern(String variable, char operator) {
            UriTypeMatchTemplate matchTemplate = getMatchTemplate();
            Class[] variableTypes = matchTemplate.variableTypes;
            try {
                if(variableIndex < variableTypes.length) {
                    Class variableType = variableTypes[variableIndex];
                    return matchTemplate.resolveTypePattern(variableType, variable, operator);
                }
                else {
                    return super.getVariablePattern(variable, operator);
                }
            } finally {
                variableIndex++;
            }
        }
    }

}
