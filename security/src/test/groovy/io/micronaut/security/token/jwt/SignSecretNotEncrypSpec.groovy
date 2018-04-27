package io.micronaut.security.token.jwt

import com.nimbusds.jwt.EncryptedJWT
import com.nimbusds.jwt.JWTParser
import com.nimbusds.jwt.SignedJWT
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.authentication.UserDetails
import io.micronaut.security.authorization.AuthorizationUtils
import io.micronaut.security.token.jwt.config.JwtConfiguration
import io.micronaut.security.token.jwt.config.JwtGeneratorConfiguration
import io.micronaut.security.token.jwt.encryption.JwtGeneratorEncryptionConfiguration
import io.micronaut.security.token.jwt.generator.JwtTokenGenerator
import io.micronaut.security.token.jwt.generator.claims.JWTClaimsSetGenerator
import io.micronaut.security.token.jwt.signature.JwtGeneratorSignatureConfiguration
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class SignSecretNotEncrypSpec extends Specification implements AuthorizationUtils, PlainJwtGenerator {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'jwt',
            'endpoints.health.enabled': true,
            'endpoints.health.sensitive': true,
            'micronaut.security.enabled': true,
            'micronaut.security.endpoints.login': true,
            'micronaut.security.token.enabled': true,
            'micronaut.security.token.jwt.enabled': true,
            'micronaut.security.token.jwt.generator.signature.enabled': true,
            'micronaut.security.token.jwt.generator.signature.secret': 'qrD6h8K6S9503Q06Y6Rfk21TErImPYqa',
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
        jwtGeneratorSignatureConfiguration.getSecret() != null

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
        token != plainJwt('user', [])

        when:
        get("/health", token)

        then:
        noExceptionThrown()
    }
}
