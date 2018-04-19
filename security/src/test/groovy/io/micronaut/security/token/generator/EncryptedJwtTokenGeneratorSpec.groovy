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

import com.nimbusds.jose.EncryptionMethod
import com.nimbusds.jose.JWEAlgorithm
import com.nimbusds.jose.JWSAlgorithm
import io.micronaut.security.authentication.UserDetails
import io.micronaut.security.token.validator.EncryptedJwtTokenValidator
import org.pac4j.core.profile.jwt.JwtClaims
import spock.lang.Specification

class EncryptedJwtTokenGeneratorSpec extends Specification {

    def "Encrypted JWT is generated and validated"() {
        given:
        File publicKey = new File('src/test/resources/public_key.der')
        File privateKey = new File('src/test/resources/private_key.der')
        final Integer defaultExpiration = 3600
        TokenConfiguration tokenConfiguration = Stub(TokenConfiguration) {
            getRolesClaimName() >> ['roles']
            getJwsAlgorithm() >> JWSAlgorithm.HS256
            getSecret() >> 'qrD6h8K6S9503Q06Y6Rfk21TErImPYqa'
            getDefaultExpiration() >> 3600
            getRefreshTokenExpiration() >> null
        }
        TokenEncryptionConfiguration tokenEncryptionConfiguration = Stub(TokenEncryptionConfiguration) {
            isEnabled() >> true
            getPublicKeyPath() >> publicKey
            getPrivateKeyPath() >> privateKey
            getEncryptionMethod() >> EncryptionMethod.A128GCM
            getJweAlgorithm() >> JWEAlgorithm.RSA_OAEP_256
        }

        EncryptionKeyProvider rsaKeyProvider = new FileRSAKeyProvider(tokenEncryptionConfiguration)
        rsaKeyProvider.initialize()

        TokenGenerator generator = new EncryptedJwtTokenGenerator(tokenConfiguration,
                new JWTClaimsSetGenerator(),
                tokenEncryptionConfiguration,
                rsaKeyProvider
        )

        UserDetails userDetails = new UserDetails(username: 'sherlock', roles: ['ROLE_DETECTIVE'])

        expect:
        publicKey.exists()
        privateKey.exists()

        when:
        String jwtToken = generator.generateToken(userDetails, defaultExpiration)

        then:
        noExceptionThrown()

        when:
        EncryptedJwtTokenValidator tokenValidator = new EncryptedJwtTokenValidator(rsaKeyProvider)
        Optional<Map<String,Object>> claims = tokenValidator.validateTokenAndGetClaims(jwtToken)

        then:
        claims.isPresent()
        claims.get().get('roles') == ['ROLE_DETECTIVE']
        claims.get().get(JwtClaims.SUBJECT) == 'sherlock'
    }
}
