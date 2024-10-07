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
package io.micronaut.http.tck;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;

import java.util.*;
import java.util.function.Consumer;

/**
 * Utility class to verify assertions given an HTTP Response.
 * @author Sergio del Amo
 * @since 3.8.0
 */
@Experimental
public final class HttpResponseAssertion {
    private final HttpStatus httpStatus;
    private final Map<String, String> headers;
    private final List<BodyAssertion<?, ?>> bodyAssertions;

    @Nullable
    private final Consumer<HttpResponse<?>> responseConsumer;

    private HttpResponseAssertion(HttpStatus httpStatus,
                                  Map<String, String> headers,
                                  List<BodyAssertion<?, ?>> bodyAssertions,
                                  @Nullable Consumer<HttpResponse<?>> responseConsumer) {
        this.httpStatus = httpStatus;
        this.headers = headers;
        this.bodyAssertions = bodyAssertions;
        this.responseConsumer = responseConsumer;
    }

    @NonNull
    public Optional<Consumer<HttpResponse<?>>> getResponseConsumer() {
        return Optional.ofNullable(responseConsumer);
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

    public List<BodyAssertion<?, ?>> getBody() {
        return bodyAssertions;
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
        private List<BodyAssertion<?, ?>> bodyAssertions;

        private Consumer<HttpResponse<?>> responseConsumer;

        /**
         *
         * @param responseConsumer HTTP Response Consumer
         * @return HTTP Response Assertion Builder
         */
        public Builder assertResponse(Consumer<HttpResponse<?>> responseConsumer) {
            this.responseConsumer = responseConsumer;
            return this;
        }

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
         * @param headerName Header Name
         * @param headerValue Header Value
         * @return HTTP Response Assertion Builder
         */
        public Builder header(String headerName, String headerValue) {
            if (this.headers == null) {
                this.headers = new HashMap<>();
            }
            this.headers.put(headerName, headerValue);
            return this;
        }

        /**
         *
         * @param containsBody Response Body
         * @return HTTP Response Assertion Builder
         */
        public Builder body(String containsBody) {
            return body(BodyAssertion.builder().body(containsBody).contains());
        }

        /**
         *
         * @param bodyAssertion Response Body Assertion
         * @return HTTP Response Assertion Builder
         */
        public Builder body(BodyAssertion<?, ?> bodyAssertion) {
            if (this.bodyAssertions == null) {
                this.bodyAssertions = new ArrayList<>();
            }
            this.bodyAssertions.add(bodyAssertion);
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
            return new HttpResponseAssertion(Objects.requireNonNull(httpStatus), headers, bodyAssertions, responseConsumer);
        }
    }
}
