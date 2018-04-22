/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.security.authentication;

import io.micronaut.context.annotation.Primary;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.hateos.Link;
import io.micronaut.http.hateos.VndError;
import io.micronaut.http.server.exceptions.ExceptionHandler;

import javax.inject.Singleton;


/**
 * Handles the server response when an {@link AuthenticationException} is thrown.
 *
 * @author James Kleeh
 * @since 1.0
 */
@Singleton
@Primary
@Produces
public class AuthenticationExceptionHandler implements ExceptionHandler<AuthenticationException, HttpResponse> {

    @Override
    public HttpResponse handle(HttpRequest request, AuthenticationException exception) {
        VndError error = new VndError(exception.getMessage());
        error.link(Link.SELF, Link.of(request.getUri()));
        return HttpResponse.unauthorized().body(error);
    }
}

