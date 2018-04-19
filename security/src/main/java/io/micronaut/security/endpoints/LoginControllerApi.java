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

package io.micronaut.security.endpoints;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.authentication.UsernamePasswordCredentials;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public interface LoginControllerApi {

    /**
     * Attempts to authenticate the with the supplied credentials.
     *
     * @param usernamePasswordCredentials An instance of {@link UsernamePasswordCredentials} in the body payload
     * @return An Http response
     */
    @Post(LoginController.LOGIN_PATH)
    HttpResponse login(@Body UsernamePasswordCredentials usernamePasswordCredentials);
}
