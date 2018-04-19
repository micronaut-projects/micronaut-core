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

package io.micronaut.security.token.generator

import io.micronaut.security.authentication.UserDetails
import org.pac4j.core.profile.jwt.JwtClaims
import spock.lang.Specification

class JWTClaimsSetGeneratorSpec extends Specification {

    def "generateClaims includes sub and exp claims"() {
        given:
        JWTClaimsSetGenerator generator = new JWTClaimsSetGenerator()

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
