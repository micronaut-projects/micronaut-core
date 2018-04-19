/*
 * Copyright 2017-2018 original authors
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

import javax.annotation.concurrent.Immutable;
import java.util.List;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Immutable
public class InterceptUrlMapPattern {

    public static final String TOKEN_IS_AUTHENTICATED_ANONYMOUSLY = "IS_AUTHENTICATED_ANONYMOUSLY";
    public static final String TOKEN_IS_AUTHENTICATED = "IS_AUTHENTICATED";

    private final String pattern;
    private final List<String> access;
    private final HttpMethod httpMethod;

    /**
     *
     * @param pattern e.g. /health
     * @param access e.g. ['ROLE_USER', 'ROLE_ADMIN']
     * @param httpMethod e.g. HttpMethod.GET
     */
    public InterceptUrlMapPattern(String pattern, List<String> access, HttpMethod httpMethod) {
        this.pattern = pattern;
        this.access = access;
        this.httpMethod = httpMethod;
    }

    /**
     * pattern getter.
     * @return string e.g. /health
     */
    public String getPattern() {
        return pattern;
    }

    /**
     * access getter.
     * @return e.g. ['ROLE_USER', 'ROLE_ADMIN']
     */
    public List<String> getAccess() {
        return access;
    }

    /**
     * httpMethod getter.
     * @return e.g. HttpMethod.GET
     */
    public HttpMethod getHttpMethod() {
        return httpMethod;
    }
}
