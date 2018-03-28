package io.micronaut.security.jwt;

import io.micronaut.security.UserDetails;
import org.pac4j.core.profile.jwt.JwtClaims;

import javax.inject.Singleton;
import java.util.Map;

@Singleton
public class DefaultAccessRefreshTokenGenerator implements AccessRefreshTokenGenerator {

    protected final JwtConfiguration jwtConfiguration;
    protected final TokenGenerator tokenGenerator;
    protected final TokenRenderer tokenRenderer;

    public DefaultAccessRefreshTokenGenerator(JwtConfiguration jwtConfiguration,
                                              TokenGenerator tokenGenerator,
                                              TokenRenderer tokenRenderer) {
        this.jwtConfiguration = jwtConfiguration;
        this.tokenGenerator = tokenGenerator;
        this.tokenRenderer = tokenRenderer;
    }

    @Override
    public AccessRefreshToken generate(UserDetails userDetails) {
        String accessToken = tokenGenerator.generateToken(userDetails, jwtConfiguration.getDefaultExpiration());
        String refreshToken = tokenGenerator.generateToken(userDetails, jwtConfiguration.getRefreshTokenExpiration());
        return tokenRenderer.render(userDetails, accessToken, refreshToken);
    }

    @Override
    public AccessRefreshToken generate(String refreshToken, Map<String, Object> claims) {
        claims.put(JwtClaims.EXPIRATION_TIME, jwtConfiguration.getDefaultExpiration());
        String accessToken = tokenGenerator.generateToken(claims);
        return tokenRenderer.render(accessToken, refreshToken);
    }
}
