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
    private Map<String, Object> attributes = new HashMap<>();
    private String rolesKey = "roles";
    private String usernameKey = "username";

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
     *
     * @param username e.g. admin
     * @param roles e.g. ['ROLE_ADMIN', 'ROLE_USER']
     * @param attributes User's attributes
     */
    public UserDetails(String username, Collection<String> roles, Map<String, Object> attributes) {
        this.username = username;
        this.roles = roles;
        this.attributes = attributes;
    }

    /**
     * @param usernameKey e.g. key used to place the username in the attributes map
     * @param username e.g. admin
     * @param rolesKey e.g. key used to place the roles in the attributes map
     * @param roles e.g. ['ROLE_ADMIN', 'ROLE_USER']
     * @param attributes User's attributes
     */
    public UserDetails(String usernameKey, String username, String rolesKey, Collection<String> roles, Map<String, Object> attributes) {
        this.usernameKey = usernameKey;
        this.username = username;
        this.rolesKey = rolesKey;
        this.roles = roles;
        this.attributes = attributes;
    }

    /**
     *
     * @return User's attributes
     */
    public Map<String, Object> getAttributes() {
        Map<String, Object> result = this.attributes;
        result.putIfAbsent(this.rolesKey, getRoles());
        result.putIfAbsent(this.usernameKey, getUsername());
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

    /**
     * Sets user's attributes.
     * @param attributes User's attributes.
     */
    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    /**
     * @param rolesKey key used to place the roles in the attributes map
     */
    public void setRolesKey(String rolesKey) {
        this.rolesKey = rolesKey;
    }

    /**
     *
     * @param usernameKey key used to place the username in the attributes map
     */
    public void setUsernameKey(String usernameKey) {
        this.usernameKey = usernameKey;
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

        if (username != null ? !username.equals(that.username) : that.username != null) {
            return false;
        }
        if (roles != null ? !roles.equals(that.roles) : that.roles != null) {
            return false;
        }
        if (attributes != null ? !attributes.equals(that.attributes) : that.attributes != null) {
            return false;
        }
        if (rolesKey != null ? !rolesKey.equals(that.rolesKey) : that.rolesKey != null) {
            return false;
        }
        return usernameKey != null ? usernameKey.equals(that.usernameKey) : that.usernameKey == null;
    }

    @Override
    public int hashCode() {
        int result = username != null ? username.hashCode() : 0;
        result = 31 * result + (roles != null ? roles.hashCode() : 0);
        result = 31 * result + (attributes != null ? attributes.hashCode() : 0);
        result = 31 * result + (rolesKey != null ? rolesKey.hashCode() : 0);
        result = 31 * result + (usernameKey != null ? usernameKey.hashCode() : 0);
        return result;
    }
}
