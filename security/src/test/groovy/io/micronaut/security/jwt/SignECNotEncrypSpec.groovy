package io.micronaut.security.jwt

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jwt.EncryptedJWT
import com.nimbusds.jwt.JWTParser
import com.nimbusds.jwt.SignedJWT
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.authorization.AuthorizationUtils
import io.micronaut.security.jwt.config.CryptoAlgorithm
import io.micronaut.security.jwt.config.JwtConfiguration
import io.micronaut.security.jwt.generator.JwtTokenGenerator
import io.micronaut.security.jwt.signature.JwtGeneratorSignatureConfiguration
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class SignECNotEncrypSpec extends Specification implements AuthorizationUtils {

    @Shared
    File pemFile = new File('src/test/resources/ec256-key-pair.pem')

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'jwt',
            'endpoints.health.enabled': true,
            'endpoints.health.sensitive': true,
            'micronaut.security.enabled': true,
            'micronaut.security.token.bearer.enabled': true,
            'micronaut.security.endpoints.login': true,
            'micronaut.security.jwt.enabled': true,
            'micronaut.security.jwt.generator.signature.enabled': true,
            'micronaut.security.jwt.generator.signature.type': 'EC',
            'micronaut.security.jwt.generator.signature.jwsAlgorithm': 'ES256',
            'micronaut.security.jwt.generator.signature.pemPath': pemFile.absolutePath,
    ], "test")

    @Shared
    @AutoCleanup
    RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    void "test /health is secured"() {
        when:
        get("/health")

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.UNAUTHORIZED
    }

    void "/health can be accessed if authenticated"() {
        expect:
        pemFile.exists()

        when:
        JwtConfiguration jwtConfiguration = embeddedServer.applicationContext.getBean(JwtConfiguration.class)

        then:
        jwtConfiguration
        jwtConfiguration.isEnabled()

        when:
        JwtGeneratorSignatureConfiguration jwtGeneratorSignatureConfiguration = embeddedServer.applicationContext.getBean(JwtGeneratorSignatureConfiguration.class)

        then:
        jwtGeneratorSignatureConfiguration
        jwtGeneratorSignatureConfiguration.isEnabled()
        jwtGeneratorSignatureConfiguration.getType() == CryptoAlgorithm.EC
        jwtGeneratorSignatureConfiguration.getSecret() == null
        jwtGeneratorSignatureConfiguration.getJwsAlgorithm()
        jwtGeneratorSignatureConfiguration.getJwsAlgorithm().equals(JWSAlgorithm.ES256)

        when:
        JwtTokenGenerator tokenGenerator = embeddedServer.applicationContext.getBean(JwtTokenGenerator.class)

        then:
        tokenGenerator.getSignatureConfiguration() != null
        tokenGenerator.getEncryptionConfiguration() == null

        when:
        String token = loginWith(client,'user', 'password')

        then:
        token
        !(JWTParser.parse(token) instanceof EncryptedJWT)
        JWTParser.parse(token) instanceof SignedJWT

        when:
        get("/health", token)

        then:
        noExceptionThrown()
    }
}
