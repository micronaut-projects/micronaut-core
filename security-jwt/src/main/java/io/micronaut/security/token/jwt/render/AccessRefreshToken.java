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

/**
 * Stores the combination of access and refresh tokens.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public class AccessRefreshToken {

    private String accessToken;
    private String refreshToken;

    /**
     * Necessary for JSON serialization.
     */
    public AccessRefreshToken() { }

    /**
     *
     * @param accessToken JWT token
     * @param refreshToken JWT token
     */
    public AccessRefreshToken(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    /**
     * accesToken getter.
     * @return The access token
     */
    public String getAccessToken() {
        return accessToken;
    }

    /**
     * accesToken setter.
     * @return The refresh token
     */
    public String getRefreshToken() {
        return refreshToken;
    }
}
