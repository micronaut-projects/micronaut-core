package io.micronaut.security.token.generator

import com.nimbusds.jose.EncryptionMethod
import com.nimbusds.jose.JWEAlgorithm
import com.nimbusds.jose.JWSAlgorithm
import io.micronaut.security.authentication.UserDetails
import io.micronaut.security.token.configuration.CryptoAlgorithm
import io.micronaut.security.token.configuration.EncryptionConfigurationGenerator
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
        def tokenConfiguration = Stub(TokenConfiguration) {
            getRolesClaimName() >> 'roles'
            getAccessTokenExpiration() >> defaultExpiration
        }
        TokenSignatureConfiguration tokenSignatureConfiguration = Stub(TokenSignatureConfiguration) {
            isEnabled() >> true
            getType() >> CryptoAlgorithm.SECRET
            getJwsAlgorithm() >> JWSAlgorithm.HS256
            getSecret() >> 'qrD6h8K6S9503Q06Y6Rfk21TErImPYqa'
        }

        TokenEncryptionConfiguration tokenEncryptionConfiguration = Stub(TokenEncryptionConfiguration) {
            isEnabled() >> false
            getEncryptionMethod() >> EncryptionMethod.A128GCM
            getJweAlgorithm() >> JWEAlgorithm.RSA_OAEP_256
            getType() >> CryptoAlgorithm.RSA
        }


        TokenGenerator generator = new JwtTokenGenerator(new SignatureConfigurationGenerator(tokenSignatureConfiguration),
                new EncryptionConfigurationGenerator(tokenEncryptionConfiguration, null),
                new JWTClaimsSetGenerator(tokenConfiguration)
        )
        UserDetails userDetails = new UserDetails('sherlock', ['ROLE_DETECTIVE'])

        when:
        String jwtToken = generator.generateToken(userDetails, defaultExpiration)
        println jwtToken

        then:
        noExceptionThrown()

        when:
        TokenValidator tokenValidator = new JwtTokenValidator(new SignatureConfigurationGenerator(tokenSignatureConfiguration),
                new EncryptionConfigurationGenerator(tokenEncryptionConfiguration, null)
        )
        Optional<Map<String,Object>> claims = tokenValidator.validateToken(jwtToken).get().attributes

        then:
        claims.isPresent()
        claims.get().get('roles') == ['ROLE_DETECTIVE']
        claims.get().get(JwtClaims.SUBJECT) == 'sherlock'
    }
}
