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

package io.micronaut.http.uri;

import javax.annotation.Nullable;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;
import java.util.Optional;

/**
 * Enables to build encoded urls easily.
 *
 * @author Sergio del Amo
 * @since 1.1.0
 */
class UriBuilder {

    private final String QUESTION_MARK = "?";
    private final String EQUALS_SIGN = "=";
    private final String AMPERSAND = "&";

    @Nullable
    private String baseUrl;

    @Nullable
    private Map<String, Object> queryParameters;

    private boolean skipEmptyQueryParameters = false;

    /**
     * Whether to skip from the encoded uri query parameters whose value is null or an empty string.
     *
     * @return the UriBuilder instance
     */
    public UriBuilder skipEmptyQueryParameters() {
        skipEmptyQueryParameters = true;
        return this;
    }

    /**
     *
     * @param baseUrl A base url e.g. http://localhost:8080/books
     * @return the UriBuilder instance
     */
    public UriBuilder baseUrl(@Nullable String baseUrl) {
        this.baseUrl = baseUrl;
        return this;
    }

    /**
     *
     * @param queryParameters A map which you wish to encode URL
     * @return the UriBuilder instance
     */
    public UriBuilder queryParameters(@Nullable Map<String, Object> queryParameters) {
        this.queryParameters = queryParameters;
        return this;
    }

    /**
     *
     * @return an encoded URI
     */
    public String build() {
        StringBuilder sb = new StringBuilder();

        getBaseUrl().ifPresent(sb::append);
        getQueryParameters().ifPresent(params -> {
            if (!params.isEmpty()) {
                if (!skipEmptyQueryParameters ||
                        params.keySet().stream().anyMatch(key -> !isEmpty(params.get(key)))) {
                    sb.append(QUESTION_MARK);
                }
                params.keySet().stream()
                        .filter(k -> {
                            if (!skipEmptyQueryParameters) {
                                return true;
                            }
                            return !isEmpty(params.get(k));
                        })
                        .map(k -> encode(k) + EQUALS_SIGN + encode(params.get(k)))
                        .reduce((a, b) -> a + AMPERSAND + b)
                        .ifPresent(sb::append);
            }
        });
        return sb.toString();
    }

    private boolean isEmpty(Object object) {
        if (object == null) {
            return true;
        }
        if (object instanceof String) {
            if (((String) object).trim().equals("")) {
                return true;
            }
        }
        return false;
    }

    private Optional<String> getBaseUrl() {
        return Optional.ofNullable(baseUrl);
    }

    private Optional<Map<String, Object>> getQueryParameters() {
        return Optional.ofNullable(queryParameters);
    }

    private Object encode(Object obj) {
        if (!(obj instanceof String)) {
            return obj;
        }
        try {
            return URLEncoder.encode((String) obj, "UTF-8");

        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("No available encoding", e);
        }
    }
}
