package io.micronaut.security.token.generator

import com.nimbusds.jose.EncryptionMethod
import com.nimbusds.jose.JWEAlgorithm
import com.nimbusds.jose.JWSAlgorithm
import io.micronaut.security.authentication.UserDetails
import io.micronaut.security.token.configuration.EncryptionType
import io.micronaut.security.token.configuration.EncryptionConfigurationGenerator
import io.micronaut.security.token.configuration.SignatureConfiguration
import io.micronaut.security.token.configuration.SignatureConfigurationGenerator
import io.micronaut.security.token.configuration.TokenEncryptionConfiguration
import io.micronaut.security.token.configuration.TokenSignatureConfiguration
import io.micronaut.security.token.validator.JwtTokenValidator
import io.micronaut.security.token.validator.TokenValidator
import org.pac4j.core.profile.jwt.JwtClaims
import spock.lang.Specification

class TokenGenerationAndValidationWithSignatureAndEncryptionSpec extends Specification {

    def "Encrypted JWT is generated and validated"() {
        given:
        File publicKey = new File('src/test/resources/public_key.der')
        File privateKey = new File('src/test/resources/private_key.der')

        final Integer defaultExpiration = 3600
        TokenSignatureConfiguration tokenSignatureConfiguration = Stub(TokenSignatureConfiguration) {
            isEnabled() >> true
            getType() >> SignatureConfiguration.SECRET
            getJwsAlgorithm() >> JWSAlgorithm.HS256
            getSecret() >> 'qrD6h8K6S9503Q06Y6Rfk21TErImPYqa'
        }


        TokenEncryptionConfiguration tokenEncryptionConfiguration = Stub(TokenEncryptionConfiguration) {
            isEnabled() >> true
            getType() >> EncryptionType.RSA
            getPublicKeyPath() >> publicKey
            getPrivateKeyPath() >> privateKey
            getEncryptionMethod() >> EncryptionMethod.A128GCM
            getJweAlgorithm() >> JWEAlgorithm.RSA_OAEP_256
        }
        EncryptionKeyProvider rsaKeyProvider = new FileRSAKeyProvider(tokenEncryptionConfiguration)
        rsaKeyProvider.initialize()
        TokenGenerator generator = new JwtTokenGenerator(new SignatureConfigurationGenerator(tokenSignatureConfiguration),
                new EncryptionConfigurationGenerator(tokenEncryptionConfiguration, rsaKeyProvider),
                new JWTClaimsSetGenerator()
        )

        UserDetails userDetails = new UserDetails('sherlock', ['ROLE_DETECTIVE'])

        expect:
        publicKey.exists()
        privateKey.exists()

        when:
        String jwtToken = generator.generateToken(userDetails, defaultExpiration)

        then:
        noExceptionThrown()

        when:


        TokenValidator tokenValidator = new JwtTokenValidator(new SignatureConfigurationGenerator(tokenSignatureConfiguration),
                new EncryptionConfigurationGenerator(tokenEncryptionConfiguration, rsaKeyProvider)
        )
        Optional<Map<String,Object>> claims = tokenValidator.validateTokenAndGetClaims(jwtToken)

        then:
        claims.isPresent()
        claims.get().get('roles') == ['ROLE_DETECTIVE']
        claims.get().get(JwtClaims.SUBJECT) == 'sherlock'
    }
}
