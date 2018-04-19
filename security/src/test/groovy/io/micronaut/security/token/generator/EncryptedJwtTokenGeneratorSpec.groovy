package io.micronaut.security.token.generator

import com.nimbusds.jose.EncryptionMethod
import com.nimbusds.jose.JWEAlgorithm
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
            getJwsAlgorithm() >> 'HS256'
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
        rsaKeyProvider.init()

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
