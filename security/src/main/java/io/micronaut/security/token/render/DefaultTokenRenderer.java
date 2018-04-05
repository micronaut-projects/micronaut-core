package io.micronaut.security.token.render;

import io.micronaut.security.authentication.AuthenticationSuccess;
import io.micronaut.security.token.AccessRefreshToken;
import io.micronaut.security.token.DefaultAccessRefreshToken;
import io.micronaut.security.token.UserDetailsAccessRefreshToken;

import javax.inject.Singleton;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Singleton
public class DefaultTokenRenderer implements TokenRenderer {

    @Override
    public AccessRefreshToken render(String accessToken, String refreshToken) {
        return new DefaultAccessRefreshToken(accessToken, refreshToken);
    }

    @Override
    public AccessRefreshToken render(AuthenticationSuccess authenticationSuccess, String accessToken, String refreshToken) {
        return new UserDetailsAccessRefreshToken(authenticationSuccess.getUsername(), authenticationSuccess.getRoles(), accessToken, refreshToken);
    }
}
