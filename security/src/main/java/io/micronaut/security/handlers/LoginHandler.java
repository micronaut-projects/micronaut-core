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

package io.micronaut.security.handlers;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.security.authentication.AuthenticationFailed;
import io.micronaut.security.authentication.UserDetails;

/**
 * @author Sergio del Amo
 * @since 1.0
 */
public interface LoginHandler {

    /**
     *
     * @param userDetails Authenticated user's representation.
     * @param request The {@link HttpRequest} being executed
     * @return An HTTP Response. Eg. a redirect or an JWT token rendered to the response
     */
    HttpResponse loginSuccess(UserDetails userDetails, HttpRequest<?> request);

    /**
     *
     * @param authenticationFailed Object encapsulates the Login failure
     * @return An HTTP Response. Eg. a redirect or 401 response
     */
    HttpResponse loginFailed(AuthenticationFailed authenticationFailed);
}
