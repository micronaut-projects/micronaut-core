package io.micronaut.docs.signandencrypt

import com.nimbusds.jwt.EncryptedJWT
import com.nimbusds.jwt.JWTParser
import io.micronaut.context.ApplicationContext
import io.micronaut.docs.YamlAsciidocTagCleaner
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.token.generator.TokenGenerator
import io.micronaut.security.token.jwt.AuthorizationUtils
import io.micronaut.security.token.jwt.encryption.EncryptionConfiguration
import io.micronaut.security.token.jwt.encryption.rsa.RSAEncryption
import io.micronaut.security.token.jwt.generator.JwtTokenGenerator
import io.micronaut.security.token.jwt.signature.SignatureConfiguration
import io.micronaut.security.token.jwt.signature.secret.SecretSignature
import org.yaml.snakeyaml.Yaml
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class SignSecretEncryptRSASpec extends Specification implements AuthorizationUtils, YamlAsciidocTagCleaner {

    String yamlConfig = '''\
//tag::yamlconfig[]
micronaut:
  security:
    enabled: true
    token:
      jwt:
        enabled: true
        signatures:
          secret:
            generator:
              secret: qrD6h8K6S9503Q06Y6Rfk21TErImPYqa
'''//end::yamlconfig[]

    @Shared
    File pemFile = new File('src/test/resources/rsa-2048bit-key-pair.pem')

    @Shared
    Map<String, Object> configMap = [
            'micronaut': [
                    'security': [
                            'enabled': true,
                            'token': [
                                    'jwt': [
                                        'enabled': true,
                                        'signatures': [
                                                'secret': [
                                                        'generator': [
                                                                'secret': 'qrD6h8K6S9503Q06Y6Rfk21TErImPYqa'
                                                        ]
                                                ]
                                        ]
                                    ]
                            ]
                    ]
            ]
    ]

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'signandencrypt',
            'micronaut.security.endpoints.login.enabled': true,
            'endpoints.health.enabled': true,
            'endpoints.health.sensitive': true,
            'pem.path': pemFile.absolutePath,
    ] << flatten(configMap), "test")

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
        new Yaml().load(cleanYamlAsciidocTag(yamlConfig)) == configMap
        embeddedServer.applicationContext.getBean(RSAOAEPEncryptionConfiguration.class)
        embeddedServer.applicationContext.getBean(SignatureConfiguration.class)
        embeddedServer.applicationContext.getBean(SignatureConfiguration.class, Qualifiers.byName("generator"))
        embeddedServer.applicationContext.getBean(EncryptionConfiguration.class, Qualifiers.byName("generator"))
        embeddedServer.applicationContext.getBean(TokenGenerator.class)

        when:
        JwtTokenGenerator tokenGenerator = embeddedServer.applicationContext.getBean(JwtTokenGenerator.class)

        then:
        tokenGenerator.getSignatureConfiguration() instanceof SecretSignature
        tokenGenerator.getEncryptionConfiguration() instanceof RSAEncryption

        when:
        String token = loginWith(client,'user', 'password')

        then:
        token

        and:
        JWTParser.parse(token) instanceof EncryptedJWT

        when:
        get("/health", token)

        then:
        noExceptionThrown()
    }
}
