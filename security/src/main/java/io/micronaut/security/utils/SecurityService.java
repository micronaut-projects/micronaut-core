package io.micronaut.security.utils;

import io.micronaut.security.authentication.Authentication;

import java.util.Optional;

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
