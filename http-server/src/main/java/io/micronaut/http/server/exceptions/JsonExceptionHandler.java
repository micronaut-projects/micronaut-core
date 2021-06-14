/*
 * Copyright 2017-2020 original authors
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

import com.fasterxml.jackson.core.JsonProcessingException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.response.Error;
import io.micronaut.http.server.exceptions.response.ErrorContext;
import io.micronaut.http.server.exceptions.response.ErrorResponseProcessor;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Optional;

/**
 * Default exception handler for JSON processing errors.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Produces
@Singleton
public class JsonExceptionHandler implements ExceptionHandler<JsonProcessingException, Object> {

    private final ErrorResponseProcessor<?> responseProcessor;

    /**
     * Constructor.
     * @param responseProcessor Error Response Processor
     */
    @Inject
    public JsonExceptionHandler(ErrorResponseProcessor<?> responseProcessor) {
        this.responseProcessor = responseProcessor;
    }

    @Override
    public Object handle(HttpRequest request, JsonProcessingException exception) {
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
