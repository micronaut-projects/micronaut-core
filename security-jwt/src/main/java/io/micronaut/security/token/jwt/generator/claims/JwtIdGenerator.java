package io.micronaut.security.token.jwt.generator.claims;

/**
 * Generates the "jti" (JWT ID) claim, which provides a unique identifier for the JWT.
 * @see <a href="https://tools.ietf.org/html/rfc7519#section-4.1">4.1.7. "jti" (JWT ID) Claim</a>
 * @author Sergio del Amo
 * @version 1.0
 */
public interface JwtIdGenerator {

    /**
     *
     * @return a case-sensitive String which is used as a unique identifier for the JWT.
     */
    String generateJtiClaim();
}
