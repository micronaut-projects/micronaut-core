package io.micronaut.security;

import java.io.Serializable;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public class UsernamePassword implements Serializable, AuthenticationRequest {
    String username;
    String password;

    public UsernamePassword() {}
    public UsernamePassword(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
