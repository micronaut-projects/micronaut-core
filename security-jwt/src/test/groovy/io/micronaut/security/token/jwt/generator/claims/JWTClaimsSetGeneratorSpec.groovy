/*
 * Copyright 2017-2018 original authors
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
package io.micronaut.security.token.jwt.generator.claims

import io.micronaut.security.authentication.UserDetails
import io.micronaut.security.token.config.TokenConfiguration
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
