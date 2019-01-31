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

import java.util.HashMap;
import java.util.Map;

/**
 * Adapter from {@link UserDetails} to {@link Authentication}.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public class AuthenticationUserDetailsAdapter implements Authentication {

    private static final String DEFAULT_USERNAME = "username";
    private static final String DEFAULT_ROLESNAME = "roles";

    private UserDetails userDetails;
    private String username = DEFAULT_USERNAME;
    private String rolesNames = DEFAULT_ROLESNAME;

    /**
     *
     * @param userDetails Authenticated user's representation.
     */
    public AuthenticationUserDetailsAdapter(UserDetails userDetails) {
        this.userDetails = userDetails;
    }

    @Override
    public Map<String, Object> getAttributes() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(getRolesNames(), userDetails.getRoles());
        attributes.put(getUsername(), userDetails.getUsername());
        return attributes;
    }

    @Override
    public String getName() {
        return userDetails.getUsername();
    }

    /**
     *
     * @param rolesName The key user for roles
     */
    public void setRolesNames(String rolesName) {
        this.rolesNames = rolesName;
    }

    /**
     *
     * @return The key use for username
     */
    public String getUsername() {
        return username;
    }

    /**
     *
     * @param username The key use for username
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     *
     * @return The key use for rolesNames T
     */
    public String getRolesNames() {
        return rolesNames;
    }

    /**
     *
     * @return the original userDetails used to construct the object
     */
    public UserDetails getUserDetails() {
        return userDetails;
    }
}
