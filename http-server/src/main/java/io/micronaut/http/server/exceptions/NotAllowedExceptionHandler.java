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

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.response.ErrorResponseProcessor;
import jakarta.inject.Singleton;

/**
 * Handles exception of type {@link NotAllowedException} and returns an HTTP 405 (Method Not Allowed) response with the "Allow" HTTP Header  populated to the allowed headers.
 * @author Sergio del Amo
 * @since 4.6.0
 */
@Singleton
@Produces
public class NotAllowedExceptionHandler extends ErrorResponseProcessorExceptionHandler<NotAllowedException> {
    public NotAllowedExceptionHandler(ErrorResponseProcessor<?> responseProcessor) {
        super(responseProcessor);
    }

    @Override
    protected MutableHttpResponse<?> createResponse(NotAllowedException exception) {
        return HttpResponse.notAllowedGeneric(exception.getAllowedMethods());
    }
}
