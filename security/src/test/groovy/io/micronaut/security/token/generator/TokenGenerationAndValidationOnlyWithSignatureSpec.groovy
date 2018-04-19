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
import io.micronaut.security.token.configuration.EncryptionConfiguration
import io.micronaut.security.token.configuration.EncryptionConfigurationGenerator
import io.micronaut.security.token.configuration.SignatureConfiguration
import io.micronaut.security.token.configuration.SignatureConfigurationGenerator
import io.micronaut.security.token.configuration.TokenConfiguration
import io.micronaut.security.token.configuration.TokenEncryptionConfiguration
import io.micronaut.security.token.configuration.TokenSignatureConfiguration
import io.micronaut.security.token.validator.JwtTokenValidator
import io.micronaut.security.token.validator.TokenValidator
import org.pac4j.core.profile.jwt.JwtClaims
import spock.lang.Specification

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
class TokenGenerationAndValidationOnlyWithSignatureSpec extends Specification {

    def "JWT Token is generated and validated"() {
        given:
        final Integer defaultExpiration = 3600
        TokenSignatureConfiguration tokenSignatureConfiguration = Stub(TokenSignatureConfiguration) {
            isEnabled() >> true
            getType() >> SignatureConfiguration.SECRET
            getJwsAlgorithm() >> JWSAlgorithm.HS256
            getSecret() >> 'qrD6h8K6S9503Q06Y6Rfk21TErImPYqa'
        }

        TokenEncryptionConfiguration tokenEncryptionConfiguration = Stub(TokenEncryptionConfiguration) {
            isEnabled() >> false
            getEncryptionMethod() >> EncryptionMethod.A128GCM
            getJweAlgorithm() >> JWEAlgorithm.RSA_OAEP_256
            getType() >> EncryptionConfiguration.RSA
        }


        TokenGenerator generator = new JwtTokenGenerator(new SignatureConfigurationGenerator(tokenSignatureConfiguration),
                new EncryptionConfigurationGenerator(tokenEncryptionConfiguration, null),
                new JWTClaimsSetGenerator()
        )
        UserDetails userDetails = new UserDetails(username: 'sherlock', roles: ['ROLE_DETECTIVE'])

        when:
        String jwtToken = generator.generateToken(userDetails, defaultExpiration)
        println jwtToken

        then:
        noExceptionThrown()

        when:
        TokenValidator tokenValidator = new JwtTokenValidator(new SignatureConfigurationGenerator(tokenSignatureConfiguration),
                new EncryptionConfigurationGenerator(tokenEncryptionConfiguration, null)
        )
        Optional<Map<String,Object>> claims = tokenValidator.validateTokenAndGetClaims(jwtToken)

        then:
        claims.isPresent()
        claims.get().get('roles') == ['ROLE_DETECTIVE']
        claims.get().get(JwtClaims.SUBJECT) == 'sherlock'
    }
}
