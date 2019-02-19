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
package io.micronaut.security.authentication;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
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
    private Map<String, Object> attributes;

    /**
     *
     * @param username e.g. admin
     * @param roles e.g. ['ROLE_ADMIN', 'ROLE_USER']
     */
    public UserDetails(String username, Collection<String> roles) {
        this(username, roles, null);
    }

    /**
     *
     * @param username e.g. admin
     * @param roles e.g. ['ROLE_ADMIN', 'ROLE_USER']
     * @param attributes User's attributes
     */
    public UserDetails(String username, Collection<String> roles, Map<String, Object> attributes) {
        if (username == null || roles == null) {
            throw new IllegalArgumentException("Cannot construct a UserDetails with a null username or authorities");
        }
        this.username = username;
        this.roles = roles;
        this.attributes = attributes;
    }

    /**
     * @param rolesKey the key for the roles attribute
     * @param usernameKey the key for the username attribute
     * @return User's attributes
     */
    public Map<String, Object> getAttributes(String rolesKey, String usernameKey) {
        Map<String, Object> result = attributes == null ? new HashMap<>() : new HashMap<>(attributes);
        result.putIfAbsent(rolesKey, getRoles());
        result.putIfAbsent(usernameKey, getUsername());
        return result;
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
        if (username == null) {
            throw new IllegalArgumentException("Cannot set username to null");
        }
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
        if (roles == null) {
            throw new IllegalArgumentException("Cannot set roles to null");
        }
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

    /**
     * Sets user's attributes.
     * @param attributes User's attributes.
     */
    public void setAttributes(@Nullable Map<String, Object> attributes) {
        this.attributes = attributes;
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

        return username.equals(that.username);
    }

    @Override
    public int hashCode() {
        return username.hashCode();
    }
}
