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

package io.micronaut.security.token.jwt.render;

import io.micronaut.security.authentication.UserDetails;
import javax.inject.Singleton;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Singleton
public class BearerTokenRenderer implements TokenRenderer {

    @Override
    public AccessRefreshToken render(Integer expiresIn, String accessToken, String refreshToken) {
        return new AccessRefreshToken(accessToken, refreshToken);
    }

    @Override
    public AccessRefreshToken render(UserDetails userDetails, Integer expiresIn, String accessToken, String refreshToken) {
        return new BearerAccessRefreshToken(userDetails.getUsername(), userDetails.getRoles(), expiresIn, accessToken, refreshToken);
    }
}
