package io.micronaut.security.authentication;

import java.util.Map;

/**
 * Represents the state of an authentication.
 *
 * @author James Kleeh
 * @since 1.0
 */
public interface Authentication {

    /**
     * The identity of the authentication. Typically a username.
     *
     * @return The authentication id
     */
    String getId();

    /**
     * @return Any additional attributes in the authentication
     */
    Map<String, Object> getAttributes();
}
