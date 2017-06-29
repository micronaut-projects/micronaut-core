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
import java.util.List;
import java.util.regex.Pattern;

/**
 * Extends {@link UriTemplate} and adds the ability to match a URI to a given template.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class UriMatchTemplate extends UriTemplate {

    private StringBuilder pattern;
    private final Pattern matchPattern;
    /**
     * Construct a new URI template for the given template
     *
     * @param templateString The template string
     */
    public UriMatchTemplate(CharSequence templateString) {
        super(templateString);
        this.matchPattern = Pattern.compile(pattern.toString());
    }

    /**
     * Match the given {@link URI} object
     * @param uri The URI
     * @return True if it matches
     */
    public boolean matches(URI uri) {
        return matches(uri.toString());
    }

    /**
     * Match the given URI string
     *
     * @param uri The uRI
     * @return True if it matches
     */
    boolean matches(String uri) {
        return matchPattern.matcher(uri).matches();
    }

    @Override
    protected UrlTemplateParser createParser(String templateString) {
        this.pattern = new StringBuilder();
        return new UrlMatchTemplateParser(templateString, this);
    }

    protected static class UrlMatchTemplateParser extends UrlTemplateParser {
        final UriMatchTemplate matchTemplate;
        UrlMatchTemplateParser(String templateText, UriMatchTemplate matchTemplate) {
            super(templateText);
            this.matchTemplate = matchTemplate;
        }


        @Override
        protected void addRawContentSegment(List<PathSegment> segments, String value) {
            String escaped = value.replace("/", "\\/");
            matchTemplate.pattern.append(escaped);
            super.addRawContentSegment(segments, value);
        }

        @Override
        protected void addVariableSegment(List<PathSegment> segments, String variable, String prefix, String delimiter, boolean encode, boolean repeatPrefix, String modifierStr, char modifierChar, String previousDelimiter) {
            StringBuilder pattern = matchTemplate.pattern;
            if(prefix != null) {
                if(prefix.length() == 1) {
                    switch (prefix.charAt(0)) {
                        case '/': pattern.append("\\/"); break;
                        default: pattern.append(prefix);
                    }
                }
                else {
                    pattern.append(prefix);
                }
            }
            pattern.append("([^\\/]+)");
            super.addVariableSegment(segments, variable, prefix, delimiter, encode, repeatPrefix, modifierStr, modifierChar, previousDelimiter);
        }
    }
}
