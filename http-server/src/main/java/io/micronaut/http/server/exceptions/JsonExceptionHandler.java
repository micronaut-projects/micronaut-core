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

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.response.ErrorResponseProcessor;
import io.micronaut.json.JsonSyntaxException;
import jakarta.inject.Singleton;

/**
 * Default exception handler for JSON processing errors.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Produces
@Singleton
@Internal
public final class JsonExceptionHandler extends BaseJsonExceptionHandler<JsonSyntaxException> implements ExceptionHandler<JsonSyntaxException, Object> {
    /**
     * Constructor.
     *
     * @param responseProcessor Error Response Processor
     */
    public JsonExceptionHandler(ErrorResponseProcessor<?> responseProcessor) {
        super(responseProcessor);
    }
}
