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

import java.util.Collection;
import java.util.List;

/**
 * Encapsulates an Access Token response as described in <a href="https://tools.ietf.org/html/rfc6749#section-4.1.4">RFC 6749</a>.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public class BearerAccessRefreshToken extends AccessRefreshToken {

    private String username;
    private Collection<String> roles;

    /**
     * Necessary for JSON serialization.
     */
    public BearerAccessRefreshToken() { }

    /**
     *
     * @param username a string e.g. admin
     * @param roles Collection of Strings e.g. ( [ROLE_USER, ROLE_ADMIN] )
     * @param expiresIn Access Token expiration
     * @param accessToken JWT token
     * @param refreshToken  JWT token
     * @param tokenType Type of token
     */
    public BearerAccessRefreshToken(String username,
                                    Collection<String> roles,
                                    Integer expiresIn,
                                    String accessToken,
                                    String refreshToken,
                                    String tokenType
    ) {
        super(accessToken, refreshToken, tokenType, expiresIn);
        this.username = username;
        this.roles = roles;
    }

    /**
     * username getter.
     * @return a string e.g. admin
     */
    public String getUsername() {
        return username;
    }

    /**
     * username setter.
     * @param username e.g. admin
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * roles getter.
     * @return Collection of Strings e.g. ( [ROLE_USER, ROLE_ADMIN] )
     */
    public Collection<String> getRoles() {
        return roles;
    }

    /**
     * roles property setter.
     * @param roles list of Strings e.g. ( [ROLE_USER, ROLE_ADMIN] )
     */
    public void setRoles(List<String> roles) {
        this.roles = roles;
    }
}
