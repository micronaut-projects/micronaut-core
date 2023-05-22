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
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.response.ErrorResponseProcessor;
import jakarta.inject.Singleton;

/**
 * Default exception handler for jackson processing errors.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Produces
@Singleton
@Requires(classes = JsonProcessingException.class)
@Internal
public final class JacksonExceptionHandler extends BaseJsonExceptionHandler<JsonProcessingException> implements ExceptionHandler<JsonProcessingException, Object> {
    /**
     * Constructor.
     *
     * @param responseProcessor Error Response Processor
     */
    public JacksonExceptionHandler(ErrorResponseProcessor<?> responseProcessor) {
        super(responseProcessor);
    }
}
