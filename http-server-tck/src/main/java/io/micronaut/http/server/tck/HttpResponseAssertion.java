/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.http.server.tck;

import io.micronaut.http.HttpStatus;

import java.util.Map;
import java.util.Objects;

/**
 * Utility class to verify assertions given an HTTP Response.
 * @author Sergio del Amo
 * @since 3.8.0
 */
public final class HttpResponseAssertion {

    private final HttpStatus httpStatus;
    private final Map<String, String> headers;
    private final String body;

    private HttpResponseAssertion(HttpStatus httpStatus, Map<String, String> headers, String body) {
        this.httpStatus = httpStatus;
        this.headers = headers;
        this.body = body;
    }

    /**
     *
     * @return Expected HTTP Response Status
     */
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    /**
     *
     * @return Expected HTTP Response Headers
     */
    public Map<String, String> getHeaders() {
        return headers;
    }

    /**
     *
     * @return Expected HTTP Response body
     */
    public String getBody() {
        return body;
    }

    /**
     *
     * @return Creates an instance of {@link HttpResponseAssertion.Builder}.
     */
    public static HttpResponseAssertion.Builder builder() {
        return new HttpResponseAssertion.Builder();
    }

    /**
     * HTTP Response Assertion Builder.
     */
    public static class Builder {
        private HttpStatus httpStatus;
        private Map<String, String> headers;
        private String body;

        /**
         *
         * @param headers HTTP Headers
         * @return HTTP Response Assertion Builder
         */
        public Builder headers(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        /**
         *
         * @param body Response Body
         * @return HTTP Response Assertion Builder
         */
        public Builder body(String body) {
            this.body = body;
            return this;
        }

        /**
         *
         * @param httpStatus Response's HTTP Status
         * @return HTTP Response Assertion Builder
         */
        public Builder status(HttpStatus httpStatus) {
            this.httpStatus = httpStatus;
            return this;
        }

        /**
         *
         * @return HTTP Response Assertion
         */
        public HttpResponseAssertion build() {
            return new HttpResponseAssertion(Objects.requireNonNull(httpStatus), headers, body);
        }
    }
}
