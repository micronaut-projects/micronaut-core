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
package io.micronaut.security.token.render;

import java.util.Collection;
import java.util.List;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public class BearerTokenAccessRefreshToken extends AccessRefreshToken {

    private String username;
    private Collection<String> roles;
    private String token_type = "Bearer";
    private Integer expires_in;

    /**
     * Empty constructor.
     */
    public BearerTokenAccessRefreshToken() {}

    /**
     *
     * @param username a string e.g. admin
     * @param roles Collection of Strings e.g. ( [ROLE_USER, ROLE_ADMIN] )
     * @param accessToken JWT token
     * @param refreshToken  JWT token
     */
    public BearerTokenAccessRefreshToken(String username,
                                         Collection<String> roles,
                                         Integer expiresIn,
                                         String accessToken,
                                         String refreshToken) {
        super(accessToken, refreshToken);
        this.username = username;
        this.roles = roles;
        this.expires_in = expiresIn;
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

    public String getToken_type() {
        return token_type;
    }

    public void setToken_type(String token_type) {
        this.token_type = token_type;
    }

    public Integer getExpires_in() {
        return expires_in;
    }

    public void setExpires_in(Integer expires_in) {
        this.expires_in = expires_in;
    }
}
