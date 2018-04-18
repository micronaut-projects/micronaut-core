package io.micronaut.security.token.generator

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
            getPublicKeyPath() >> publicKey.absolutePath
            getPrivateKeyPath() >> privateKey.absolutePath
            getEncryptionMethod() >> TokenEncryptionConfigurationProperties.DEFAULT_ENCRYPTIONMETHOD
            getJweAlgorithm() >> TokenEncryptionConfigurationProperties.DEFAULT_JWEALGORITHM
        }

        RSAKeyProvider rsaKeyProvider = new FileRSAKeyProvider(tokenEncryptionConfiguration)
        rsaKeyProvider.init()

        TokenGenerator generator = new EncryptedJwtTokenGenerator(tokenConfiguration,
                new DefaultClaimsGenerator(),
                new JWTClaimsSetConverter(),
                tokenEncryptionConfiguration,
                rsaKeyProvider
        )
        generator.initialize()

        UserDetails userDetails = new UserDetails(username: 'sherlock', roles: ['ROLE_DETECTIVE'])

        expect:
        publicKey.exists()
        privateKey.exists()

        when:
        Optional<String> jwtToken = generator.generateToken(userDetails, defaultExpiration)

        then:
        jwtToken.isPresent()

        when:
        EncryptedJwtTokenValidator tokenValidator = new EncryptedJwtTokenValidator(tokenConfiguration, tokenEncryptionConfiguration, rsaKeyProvider)
        tokenValidator.initialize()
        Map<String,Object> claims = tokenValidator.validateTokenAndGetClaims(jwtToken.get())

        then:
        claims
        claims.get('roles') == ['ROLE_DETECTIVE']
        claims.get(JwtClaims.SUBJECT) == 'sherlock'
    }
}
