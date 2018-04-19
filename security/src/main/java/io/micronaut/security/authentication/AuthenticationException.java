package io.micronaut.security.authentication;

public class AuthenticationException extends RuntimeException {

    public AuthenticationException(AuthenticationResponse response) {
        super(response.getMessage().orElse(null));
    }

    public AuthenticationException() {
        super();
    }

    public AuthenticationException(String message) {
        super(message);
    }
}
