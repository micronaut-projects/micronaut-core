/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.http.server.exceptions.response;

import io.micronaut.context.annotation.DefaultImplementation;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.MutableHttpResponse;

/**
 * Creates Http responses that represent errors.
 *
 * @param <T> The response body type
 * @author James Kleeh
 * @since 2.4.0
 */
@DefaultImplementation(HateoasErrorResponseProcessor.class)
public interface ErrorResponseProcessor<T> {

    /**
     * Modifies the http response representing the error. Callers of this
     * method should return the response that was passed in {@param baseResponse},
     * however that isn't required.
     *
     * Error response processors should not set the body or content type if
     * the request method is HEAD.
     *
     * @param errorContext The error context
     * @param baseResponse The base response to retrieve information or
     *                     mutate
     * @return An error response
     */
    @NonNull
    MutableHttpResponse<T> processResponse(@NonNull ErrorContext errorContext, @NonNull MutableHttpResponse<?> baseResponse);

}
