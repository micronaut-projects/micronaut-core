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

package io.micronaut.security.authentication;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

/**
 * Authenticated user's representation.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public class UserDetails implements AuthenticationResponse {

    private String username;
    private Collection<String> roles;

    /**
     *
     * @param username e.g. admin
     * @param roles e.g. ['ROLE_ADMIN', 'ROLE_USER']
     */
    public UserDetails(String username, Collection<String> roles) {
        this.username = username;
        this.roles = roles;
    }

    /**
     * username getter.
     * @return e.g. admin
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
     * @return e.g. ['ROLE_USER', 'ROLE_ADMIN']
     */
    public Collection<String> getRoles() {
        return roles;
    }

    /**
     * roles setter.
     * @param roles e.g. ['ROLE_USER', 'ROLE_ADMIN']
     */
    public void setRoles(Collection<String> roles) {
        this.roles = roles;
    }

    @Override
    public boolean isAuthenticated() {
        return true;
    }

    @Override
    public Optional<String> getMessage() {
        return Optional.empty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        UserDetails that = (UserDetails) o;
        return Objects.equals(username, that.username) &&
                Objects.equals(roles, that.roles);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, roles);
    }

}
