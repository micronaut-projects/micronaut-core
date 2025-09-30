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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.cookie.CookieSizeExceededException;
import io.micronaut.http.server.exceptions.response.ErrorResponseProcessor;
import jakarta.inject.Singleton;

/**
 * Default handler for {@link CookieSizeExceededException} errors.
 *
 * @author Sergio del Amo
 * @since 4.9.1
 */
@Singleton
@Produces
public class CookieSizeExceededHandler extends ErrorResponseProcessorExceptionHandler<CookieSizeExceededException> {

    /**
     * Constructor.
     * @param responseProcessor Error Response Processor
     */
    public CookieSizeExceededHandler(ErrorResponseProcessor<?> responseProcessor) {
        super(responseProcessor);
    }

    @Override
    @NonNull
    protected MutableHttpResponse<?> createResponse(CookieSizeExceededException exception) {
        return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}

