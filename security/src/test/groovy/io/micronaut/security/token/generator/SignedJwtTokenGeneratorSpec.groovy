/*
 * Copyright 2017 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.security.token.generator

import io.micronaut.security.authentication.UserDetails
import io.micronaut.security.token.validator.SignedJwtTokenValidator
import org.pac4j.core.profile.jwt.JwtClaims
import spock.lang.Specification

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
class SignedJwtTokenGeneratorSpec extends Specification {

    def "JWT Token is generated and validated"() {
        given:
        final Integer defaultExpiration = 3600
        TokenConfiguration tokenConfiguration = Stub(TokenConfiguration) {
            getRolesClaimName() >> ['roles']
            getJwsAlgorithm() >> 'HS256'
            getSecret() >> 'qrD6h8K6S9503Q06Y6Rfk21TErImPYqa'
            getDefaultExpiration() >> 3600
            getRefreshTokenExpiration() >> null
        }
        TokenGenerator generator = new SignedJwtTokenGenerator(tokenConfiguration,
                new DefaultClaimsGenerator(),
                new JWTClaimsSetConverter(),
        )
        generator.initialize()
        UserDetails userDetails = new UserDetails(username: 'sherlock', roles: ['ROLE_DETECTIVE'])

        when:
        Optional<String> jwtToken = generator.generateToken(userDetails, defaultExpiration)
        println jwtToken

        then:
        jwtToken.isPresent()

        when:
        SignedJwtTokenValidator tokenValidator = new SignedJwtTokenValidator(tokenConfiguration)
        tokenValidator.initialize()
        Map<String,Object> claims = tokenValidator.validateTokenAndGetClaims(jwtToken.get())

        then:
        claims
        claims.get('roles') == ['ROLE_DETECTIVE']
        claims.get(JwtClaims.SUBJECT) == 'sherlock'
    }
}
