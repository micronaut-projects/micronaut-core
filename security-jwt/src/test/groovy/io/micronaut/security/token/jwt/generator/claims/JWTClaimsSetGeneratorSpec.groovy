package io.micronaut.security.token.jwt.generator.claims

import io.micronaut.security.authentication.UserDetails
import io.micronaut.security.config.SecurityConfiguration
import io.micronaut.security.token.config.TokenConfiguration
import io.micronaut.security.token.jwt.generator.claims.JWTClaimsSetGenerator
import io.micronaut.security.token.jwt.generator.claims.JwtClaims
import spock.lang.Specification

class JWTClaimsSetGeneratorSpec extends Specification {

    def "generateClaims includes sub and exp claims"() {
        given:
        def tokenConfiguration = Stub(TokenConfiguration) {
            getRolesName() >> 'roles'
        }
        JWTClaimsSetGenerator generator = new JWTClaimsSetGenerator(tokenConfiguration)

        when:
        Map<String, Object> claims = generator.generateClaims(new UserDetails('admin', ['ROLE_USER', 'ROLE_ADMIN']), 3600)

        then:
        claims
        claims.keySet().size() == 4
        claims.get(JwtClaims.SUBJECT)
        claims.get(JwtClaims.EXPIRATION_TIME)
        claims.get("iat")
        claims.get("roles")
    }
}
