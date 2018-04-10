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
package io.micronaut.security.token;

import io.micronaut.security.token.AccessRefreshToken;

import java.util.Collection;
import java.util.List;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public class UserDetailsAccessRefreshToken implements AccessRefreshToken {

    private String username;
    private Collection<String> roles;
    private String accessToken;
    private String refreshToken;

    public UserDetailsAccessRefreshToken() {}

    public UserDetailsAccessRefreshToken(String username, Collection<String> roles, String accessToken, String refreshToken) {
        this.username = username;
        this.roles = roles;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Collection<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    @Override
    public String getAccessToken() {
        return accessToken;
    }

    @Override
    public String getRefreshToken() {
        return refreshToken;
    }
}
