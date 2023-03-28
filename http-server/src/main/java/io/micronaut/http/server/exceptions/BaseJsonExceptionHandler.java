/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.http.server.exceptions;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.server.exceptions.response.Error;
import io.micronaut.http.server.exceptions.response.ErrorContext;
import io.micronaut.http.server.exceptions.response.ErrorResponseProcessor;

import java.util.Optional;

sealed class BaseJsonExceptionHandler<E extends Exception> implements ExceptionHandler<E, Object> permits JacksonExceptionHandler, JsonExceptionHandler {

    private final ErrorResponseProcessor<?> responseProcessor;

    BaseJsonExceptionHandler(ErrorResponseProcessor<?> responseProcessor) {
        this.responseProcessor = responseProcessor;
    }

    @Override
    public Object handle(HttpRequest request, E exception) {
        MutableHttpResponse<Object> response = HttpResponse.status(HttpStatus.BAD_REQUEST, "Invalid JSON");
        return responseProcessor.processResponse(ErrorContext.builder(request)
            .cause(exception)
            .error(new Error() {
                @Override
                public String getMessage() {
                    return "Invalid JSON: " + exception.getMessage();
                }

                @Override
                public Optional<String> getTitle() {
                    return Optional.of("Invalid JSON");
                }
            })
            .build(), response);
    }
}
