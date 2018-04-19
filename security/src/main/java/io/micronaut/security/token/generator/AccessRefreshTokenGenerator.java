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

package io.micronaut.security.token.generator;

import io.micronaut.http.HttpResponse;
import io.micronaut.security.authentication.UserDetails;
import io.micronaut.security.token.render.AccessRefreshToken;

import java.util.Map;

/**
 * Generates http responses with access and refresh
 * tokens as the body of the response.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public interface AccessRefreshTokenGenerator {

    /**
     * Generate an {@link AccessRefreshToken} response for the given
     * user details.
     *
     * @param userDetails Authenticated user's representation.
     * @return The http response
     */
    HttpResponse<AccessRefreshToken> generate(UserDetails userDetails);

    /**
     * Generate an {@link AccessRefreshToken} response for the given
     * refresh token and claims.
     *
     * @param refreshToken The refresh token
     * @param claims The claims to generate the access token
     * @return The http response
     */
    HttpResponse<AccessRefreshToken> generate(String refreshToken, Map<String, Object> claims);
}
