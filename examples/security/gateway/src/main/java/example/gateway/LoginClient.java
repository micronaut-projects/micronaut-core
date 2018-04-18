/*
 * Copyright 2017 original authors
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
package example.gateway;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.client.Client;
import io.micronaut.security.authentication.UsernamePasswordCredentials;
import io.micronaut.security.endpoints.LoginController;
import io.micronaut.security.endpoints.LoginControllerApi;
import io.micronaut.security.token.render.AccessRefreshToken;
import io.micronaut.security.token.render.BearerTokenAccessRefreshToken;

/**
 * @author Sergio del Amo
 * @since 1.0
 */
@Client(id = "security")
public interface LoginClient {

    @Post(LoginController.LOGIN_PATH)
    HttpResponse<BearerTokenAccessRefreshToken> login(@Body UsernamePasswordCredentials usernamePasswordCredentials);
}
