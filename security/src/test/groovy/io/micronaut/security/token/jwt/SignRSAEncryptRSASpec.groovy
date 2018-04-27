package io.micronaut.security.token.jwt

import com.nimbusds.jwt.EncryptedJWT
import com.nimbusds.jwt.JWTParser
import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.authorization.AuthorizationUtils
import io.micronaut.security.token.jwt.config.CryptoAlgorithm
import io.micronaut.security.token.jwt.config.JwtConfiguration
import io.micronaut.security.token.jwt.encryption.JwtGeneratorEncryptionConfiguration
import io.micronaut.security.token.jwt.generator.JwtTokenGenerator
import io.micronaut.security.token.jwt.signature.JwtGeneratorSignatureConfiguration
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class SignRSAEncryptRSASpec extends Specification implements AuthorizationUtils, PlainJwtGenerator {
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
            'micronaut.security.token.enabled': true,
            'micronaut.security.token.jwt.enabled': true,
            'micronaut.security.token.jwt.generator.signature.enabled': true,
            'micronaut.security.token.jwt.generator.signature.type': 'RSA',
            'micronaut.security.token.jwt.generator.signature.jwsAlgorithm': 'PS512',
            'micronaut.security.token.jwt.generator.signature.pemPath': pemFile.absolutePath,
            'micronaut.security.token.jwt.generator.encryption.enabled': true,
            'micronaut.security.token.jwt.generator.encryption.type': CryptoAlgorithm.RSA,
            'micronaut.security.token.jwt.generator.encryption.jweAlgorithm': 'RSA-OAEP',
            'micronaut.security.token.jwt.generator.encryption.encryptionMethod': 'A128GCM',
            'micronaut.security.token.jwt.generator.encryption.pemPath': pemFile.absolutePath,
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
        jwtGeneratorSignatureConfiguration.isEnabled()

        when:
        JwtGeneratorEncryptionConfiguration jwtGeneratorEncryptionConfiguration =
                embeddedServer.applicationContext.getBean(JwtGeneratorEncryptionConfiguration.class)

        then:
        jwtGeneratorEncryptionConfiguration
        jwtGeneratorEncryptionConfiguration.isEnabled()

        when:
        JwtTokenGenerator tokenGenerator = embeddedServer.applicationContext.getBean(JwtTokenGenerator.class)

        then:
        tokenGenerator.getSignatureConfiguration() != null
        tokenGenerator.getEncryptionConfiguration() != null

        when:
        String token = loginWith(client,'user', 'password')

        then:
        token
        JWTParser.parse(token) instanceof EncryptedJWT

        and:
        token != plainJwt('user', [])

        when:
        get("/health", token)

        then:
        noExceptionThrown()
    }
}
