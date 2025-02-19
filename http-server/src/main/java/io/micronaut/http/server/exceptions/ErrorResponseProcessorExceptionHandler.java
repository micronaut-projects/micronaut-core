/*
 * Copyright 2017-2024 original authors
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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.server.exceptions.response.ErrorContext;
import io.micronaut.http.server.exceptions.response.ErrorResponseProcessor;

/**
 * An abstract class to handle exceptions via an HTTP Response and the {@link ErrorResponseProcessor} API.
 *
 * @param <T> The throwable
 * @author Sergio del Amo
 * @since 4.6.0
 */
public abstract class ErrorResponseProcessorExceptionHandler<T extends Throwable> implements ExceptionHandler<T, HttpResponse<?>> {

    protected final ErrorResponseProcessor<?> responseProcessor;

    /**
     * Constructor.
     *
     * @param responseProcessor Error Response Processor
     */
    protected ErrorResponseProcessorExceptionHandler(ErrorResponseProcessor<?> responseProcessor) {
        this.responseProcessor = responseProcessor;
    }

    @Override
    public HttpResponse<?> handle(HttpRequest request, T exception) {
        return responseProcessor.processResponse(ErrorContext.builder(request)
                .cause(exception)
                .errorMessage(exception.getMessage())
                .build(), createResponse(exception));
    }

    @NonNull
    protected abstract MutableHttpResponse<?> createResponse(T exception);
}
