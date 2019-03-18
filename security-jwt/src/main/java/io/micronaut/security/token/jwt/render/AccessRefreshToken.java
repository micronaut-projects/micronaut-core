/*
 * Copyright 2017-2019 original authors
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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Introspected;

/**
 * Stores the combination of access and refresh tokens.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Introspected
public class AccessRefreshToken {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("refresh_token")
    private String refreshToken;

    @JsonProperty("token_type")
    private String tokenType;

    @JsonProperty("expires_in")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer expiresIn;

    /**
     * Necessary for JSON serialization.
     */
    public AccessRefreshToken() { }

    /**
     *
     * @param accessToken JWT token
     * @param refreshToken JWT token
     * @param tokenType Type of token
     */
    public AccessRefreshToken(String accessToken, String refreshToken, String tokenType) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenType = tokenType;
    }

    /**
     *
     * @param accessToken JWT token
     * @param refreshToken JWT token
     * @param tokenType Type of token
     * @param expiresIn Seconds until token expiration
     */
    public AccessRefreshToken(String accessToken, String refreshToken, String tokenType, Integer expiresIn) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenType = tokenType;
        this.expiresIn = expiresIn;
    }

    /**
     * accessToken getter.
     * @return The access token
     */
    public String getAccessToken() {
        return accessToken;
    }

    /**
     * refreshToken getter.
     * @return The refresh token
     */
    public String getRefreshToken() {
        return refreshToken;
    }

    /**
     * token type getter.
     * @return TokenType e.g. Bearer
     */
    public String getTokenType() {
        return tokenType;
    }

    /**
     * token type getter.
     * @return expiration time
     */
    public Integer getExpiresIn() {
        return expiresIn;
    }
}
