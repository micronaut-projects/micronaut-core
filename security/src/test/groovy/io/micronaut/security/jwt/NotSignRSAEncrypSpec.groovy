package io.micronaut.security.jwt

import com.nimbusds.jwt.EncryptedJWT
import com.nimbusds.jwt.JWTParser
import com.nimbusds.jwt.SignedJWT
import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.authorization.AuthorizationUtils
import io.micronaut.security.jwt.config.CryptoAlgorithm
import io.micronaut.security.jwt.config.JwtConfiguration
import io.micronaut.security.jwt.encryption.JwtGeneratorEncryptionConfiguration
import io.micronaut.security.jwt.generator.JwtTokenGenerator
import io.micronaut.security.jwt.signature.JwtGeneratorSignatureConfiguration
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class NotSignRSAEncrypSpec extends Specification implements AuthorizationUtils, PlainJwtGenerator {
    @Shared
    File pemFile = new File('src/test/resources/rsa-2048bit-key-pair.pem')

    @AutoCleanup
    @Shared
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'jwt',
            'endpoints.health.enabled': true,
            'endpoints.health.sensitive': true,
            'micronaut.security.enabled': true,
            'micronaut.security.endpoints.login': true,
            'micronaut.security.token.bearer.enabled': true,
            'micronaut.security.jwt.enabled': true,
            'micronaut.security.jwt.generator.encryption.enabled': true,
            'micronaut.security.jwt.generator.encryption.type': CryptoAlgorithm.RSA,
            'micronaut.security.jwt.generator.encryption.jweAlgorithm': 'RSA-OAEP',
            'micronaut.security.jwt.generator.encryption.encryptionMethod': 'A128GCM',
            'micronaut.security.jwt.generator.encryption.pemPath': pemFile.absolutePath,
    ], "test")

    @AutoCleanup
    @Shared
    RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    void "/health can be accessed if authenticated"() {
        when:
        JwtConfiguration jwtConfiguration = embeddedServer.applicationContext.getBean(JwtConfiguration.class)

        then:
        jwtConfiguration
        jwtConfiguration.isEnabled()

        when:
        JwtGeneratorSignatureConfiguration jwtGeneratorSignatureConfiguration =
                embeddedServer.applicationContext.getBean(JwtGeneratorSignatureConfiguration.class)

        then:
        jwtGeneratorSignatureConfiguration
        !jwtGeneratorSignatureConfiguration.isEnabled()

        when:
        JwtGeneratorEncryptionConfiguration jwtGeneratorEncryptionConfiguration =
                embeddedServer.applicationContext.getBean(JwtGeneratorEncryptionConfiguration.class)

        then:
        jwtGeneratorEncryptionConfiguration
        jwtGeneratorEncryptionConfiguration.isEnabled()

        when:
        JwtTokenGenerator tokenGenerator = embeddedServer.applicationContext.getBean(JwtTokenGenerator.class)

        then:
        tokenGenerator.getSignatureConfiguration() == null
        tokenGenerator.getEncryptionConfiguration() != null

        when:
        String token = loginWith(client,'user', 'password')

        then:
        token
        JWTParser.parse(token) instanceof EncryptedJWT
        !(JWTParser.parse(token) instanceof SignedJWT)
        token != plainJwt('user', [])

        when:
        get("/health", token)

        then:
        noExceptionThrown()
    }
}
