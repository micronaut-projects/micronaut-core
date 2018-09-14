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

package io.micronaut.security.utils;

import io.micronaut.security.authentication.Authentication;

import java.util.Optional;

/**
 * Provides a set of convenient methods related to authentication and authorization.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public interface SecurityService {
    /**
     * Get the username of the current user.
     *
     * @return the username of the current user or Optional.empty if not authenticated
     */
    Optional<String> username();

    /**
     * Retrieves {@link io.micronaut.security.authentication.Authentication} if authenticated.
     *
     * @return the {@link io.micronaut.security.authentication.Authentication} of the current user
     */
    Optional<Authentication> getAuthentication();

    /**
     * Check if a user is authenticated.
     *
     * @return true if the user is authenticated, false otherwise
     */
    boolean isAuthenticated();

    /**
     * If the current user has a specific role.
     *
     * @param role the role to check
     * @return true if the current user has the role, false otherwise
     */
    boolean hasRole(String role);

    /**
     * If the current user has a specific role.
     *
     * @param role the authority to check
     * @param  rolesKey The map key to be used in the authentications attributes. E.g. "roles".
     * @return true if the current user has the authority, false otherwise
     */
    boolean hasRole(String role, String rolesKey);
}
