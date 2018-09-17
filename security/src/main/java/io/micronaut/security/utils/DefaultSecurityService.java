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

import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.filters.SecurityFilter;

import javax.inject.Singleton;
import java.security.Principal;
import java.util.Optional;
import java.util.List;

/**
 * Default implementation of {@link io.micronaut.security.utils.SecurityService}. It uses {@link ServerRequestContext#currentRequest()} to retrieve the {@link io.micronaut.security.authentication.Authentication} object if any.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Singleton
public class DefaultSecurityService implements SecurityService {

    /**
     * Get the username of the current user.
     *
     * @return the username of the current user
     */
    @Override
    public Optional<String> username() {
        return getAuthentication().map(Principal::getName);
    }

    /**
     * Retrieves {@link io.micronaut.security.authentication.Authentication} if authenticated.
     *
     * @return the {@link io.micronaut.security.authentication.Authentication} of the current user
     */
    @Override
    public Optional<Authentication> getAuthentication() {
        return ServerRequestContext.currentRequest().flatMap(request -> request.getAttribute(SecurityFilter.AUTHENTICATION, Authentication.class));
    }


    /**
     * Check if a user is authenticated.
     *
     * @return true if the user is authenticated, false otherwise
     */
    @Override
    public boolean isAuthenticated() {
        return getAuthentication().isPresent();
    }

    /**
     * If the current user has a specific role.
     *
     * @param role the role to check
     * @return true if the current user has the role, false otherwise
     */
    @Override
    public boolean hasRole(String role) {
        return hasRole(role, "roles");
    }

    /**
     * If the current user has a specific role.
     *
     * @param role the authority to check
     * @param  rolesKey The map key to be used in the authentications attributes. E.g. "roles".
     * @return true if the current user has the authority, false otherwise
     */
    @Override
    public boolean hasRole(String role, String rolesKey) {
        if (role == null || rolesKey == null) {
            return false;
        }
        return getAuthentication().map(authentication -> {
            if (authentication.getAttributes() != null && authentication.getAttributes().containsKey(rolesKey)) {
                Object authorities = authentication.getAttributes().get(rolesKey);
                if (authorities instanceof List) {
                    return ((List) authorities).contains(role);
                } else if (authorities instanceof String) {
                    return ((String) authorities).equalsIgnoreCase(role);
                }
            }
            return false;
        }).orElse(false);
    }

}
