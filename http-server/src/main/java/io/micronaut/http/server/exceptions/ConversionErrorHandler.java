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

import io.micronaut.context.annotation.Primary;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.http.hateoas.Link;

import javax.inject.Singleton;

/**
 * Handles exception of type {@link ConversionErrorException}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Primary
@Produces
public class ConversionErrorHandler implements ExceptionHandler<ConversionErrorException, HttpResponse> {
    @Override
    public HttpResponse handle(HttpRequest request, ConversionErrorException exception) {
        JsonError error = new JsonError(exception.getMessage());
        error.path('/' + exception.getArgument().getName());
        error.link(Link.SELF, Link.of(request.getUri()));

        return HttpResponse.badRequest(error);
    }
}
