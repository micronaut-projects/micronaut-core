package io.micronaut.security.jwt;

import io.micronaut.security.UserDetails;

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
    public AccessRefreshToken render(UserDetails userDetails, String accessToken, String refreshToken) {
        return new UserDetailsAccessRefreshToken(userDetails.getUsername(), userDetails.getRoles(), accessToken, refreshToken);
    }
}
