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
        JWTClaimsSetGenerator generator = new JWTClaimsSetGenerator(tokenConfiguration, null, null)

        when:
        Map<String, Object> claims = generator.generateClaims(new UserDetails('admin', ['ROLE_USER', 'ROLE_ADMIN']), 3600)
        List<String> expectedClaimsNames = [JwtClaims.SUBJECT,
                                           JwtClaims.ISSUED_AT,
                                           JwtClaims.EXPIRATION_TIME,
                                           JwtClaims.NOT_BEFORE,
                                           "roles"]
        then:
        claims
        claims.keySet().size() == expectedClaimsNames.size()
        expectedClaimsNames.each { String claimName ->
            assert claims.get(claimName)
        }
    }
}
