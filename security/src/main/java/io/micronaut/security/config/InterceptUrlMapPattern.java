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
package io.micronaut.security.config;

import io.micronaut.http.HttpMethod;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public class InterceptUrlMapPattern {
    public static final String TOKEN_IS_AUTHENTICATED_ANONYMOUSLY = "IS_AUTHENTICATED_ANONYMOUSLY";
    public static final String TOKEN_IS_AUTHENTICATED = "IS_AUTHENTICATED";

        private String pattern;
    private List<String> access;
    private HttpMethod httpMethod;

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public List<String> getAccess() {
        return access;
    }

    public void setAccess(List<String> access) {
        this.access = access;
    }

    public HttpMethod getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(HttpMethod httpMethod) {
        this.httpMethod = httpMethod;
    }

    public InterceptUrlMapPattern(String pattern, List<String> access, HttpMethod httpMethod) {
        this.pattern = pattern;
        this.access = access;
        this.httpMethod = httpMethod;
    }

    private static String invalidInterceptUrlMapPatternMessage(Map m) {
        StringBuilder sb = new StringBuilder();
        sb.append("Invalid intercept Url Map configuration");
        sb.append(m.toString());
        return sb.toString();
    }

    static InterceptUrlMapPattern of(Map m) {
        String pattern = null;
        if ( m.containsKey("pattern") ) {
            Object patternObj = m.get("pattern");
            if (patternObj instanceof String) {
                pattern = (String) patternObj;
            }
        }
        if ( pattern == null ) {
            throw new IllegalArgumentException(invalidInterceptUrlMapPatternMessage(m));
        }
        List<String> access = new ArrayList<>();
        if ( m.containsKey("access") ) {
            Object accessObj = m.get("access");
            if (accessObj instanceof List) {
                for ( Object accessEntryObj : (List) accessObj ) {
                    if ( accessEntryObj instanceof String ) {
                        access.add((String) accessEntryObj);
                    } else {
                        throw new IllegalArgumentException(invalidInterceptUrlMapPatternMessage(m));
                    }
                }
            }
        }
        if ( access.isEmpty() ) {
            throw new IllegalArgumentException(invalidInterceptUrlMapPatternMessage(m));
        }
        HttpMethod httpMethod = HttpMethod.GET;
        if ( m.containsKey("httpMethod") ) {
            Object httpMethodObj = m.get("httpMethod");
            if ( httpMethodObj instanceof String ) {
                httpMethod =  HttpMethod.valueOf((String) httpMethodObj);
            }
        }
        return new InterceptUrlMapPattern(pattern, access, httpMethod);
    }
}
