/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.http.server.netty.multipart;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ErrorExceptionHandler;
import io.micronaut.http.server.exceptions.response.Error;
import io.micronaut.http.server.exceptions.response.ErrorResponseProcessor;
import io.netty.contrib.multipart.FormDecoderException;
import jakarta.inject.Singleton;

/**
 * Answers malformed multipart input with a bad request response.
 */
@Internal
@Singleton
@Produces
final class FormDecoderExceptionHandler extends ErrorExceptionHandler<FormDecoderException> {
    FormDecoderExceptionHandler(ErrorResponseProcessor<?> responseProcessor) {
        super(responseProcessor);
    }

    @Override
    protected Error error(FormDecoderException exception) {
        return () -> exception.getMessage() == null ? "Malformed multipart request" : exception.getMessage();
    }
}
