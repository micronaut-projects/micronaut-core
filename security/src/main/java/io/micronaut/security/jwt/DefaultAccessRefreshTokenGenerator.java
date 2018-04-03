package io.micronaut.security.jwt;

import io.micronaut.security.UserDetails;
import org.pac4j.core.profile.jwt.JwtClaims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.Date;
import java.util.Map;

@Singleton
public class DefaultAccessRefreshTokenGenerator implements AccessRefreshTokenGenerator {
    private static final Logger log = LoggerFactory.getLogger(DefaultAccessRefreshTokenGenerator.class);

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
        claims.put(JwtClaims.EXPIRATION_TIME, expirationDate());
        String accessToken = tokenGenerator.generateToken(claims);
        return tokenRenderer.render(accessToken, refreshToken);
    }

    Date expirationDate() {
        Integer expiration = jwtConfiguration.getDefaultExpiration();
        Date now = new Date();
        log.debug("Setting expiration to {}", expiration.toString());
        return new Date(now.getTime() + (expiration * 1000));
    }
}
