/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http;

/**
 * A factory interface for creating {@link MutableHttpResponse} instances.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface HttpResponseFactory {

    /**
     * The default {@link HttpResponseFactory} instance.
     */
    HttpResponseFactory INSTANCE = DefaultHttpFactories.resolveDefaultResponseFactory();

    /**
     * Creates an {@link io.micronaut.http.HttpStatus#OK} response with a body.
     *
     * @param body The body
     * @param <T>  The body type
     * @return The ok response with the given body
     */
    <T> MutableHttpResponse<T> ok(T body);

    /**
     * Return a response for the given status.
     *
     * @param status The status
     * @param reason An alternatively reason message
     * @param <T>    The response type
     * @return The response
     */
    <T> MutableHttpResponse<T> status(HttpStatus status, String reason);

    /**
     * Return a response for the given status.
     *
     * @param status The status
     * @param body   The body
     * @param <T>    The body type
     * @return The response
     */
    <T> MutableHttpResponse<T> status(HttpStatus status, T body);

    /**
     * @param <T> The response type
     * @return The ok response
     */
    default <T> MutableHttpResponse<T> ok() {
        return ok(null);
    }

    /**
     * @param status The status
     * @param <T>    The response type
     * @return The restus response
     */
    default <T> MutableHttpResponse<T> status(HttpStatus status) {
        return status(status, null);
    }
}
