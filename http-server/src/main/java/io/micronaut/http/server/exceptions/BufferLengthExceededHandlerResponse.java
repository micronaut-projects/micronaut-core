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

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.exceptions.BufferLengthExceededException;
import io.micronaut.http.server.exceptions.response.ErrorResponseProcessor;
import jakarta.inject.Singleton;

/**
 * Default handler for {@link BufferLengthExceededHandlerResponse} errors.
 *
 * @author Jonas Konrad
 * @since 4.5.0
 */
@Singleton
@Produces
public class BufferLengthExceededHandlerResponse extends ErrorResponseProcessorExceptionHandler<BufferLengthExceededException> {

    /**
     * Constructor.
     * @param responseProcessor Error Response Processor
     */
    public BufferLengthExceededHandlerResponse(ErrorResponseProcessor<?> responseProcessor) {
        super(responseProcessor);
    }

    @Override
    protected MutableHttpResponse<?> createResponse(BufferLengthExceededException exception) {
        return HttpResponse.status(HttpStatus.REQUEST_ENTITY_TOO_LARGE);
    }
}

