package io.micronaut.security.token.generator

import io.micronaut.security.authentication.UserDetails
import io.micronaut.security.config.SecurityConfiguration
import io.micronaut.security.jwt.generator.claims.JWTClaimsSetGenerator
import io.micronaut.security.jwt.generator.claims.JwtClaims
import spock.lang.Specification

class JWTClaimsSetGeneratorSpec extends Specification {

    def "generateClaims includes sub and exp claims"() {
        given:
        def securityConfiguration = Stub(SecurityConfiguration) {
            getRolesName() >> 'roles'
        }
        JWTClaimsSetGenerator generator = new JWTClaimsSetGenerator(securityConfiguration)

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
