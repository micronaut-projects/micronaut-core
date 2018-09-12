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
package io.micronaut.docs.signandencrypt

import com.nimbusds.jwt.EncryptedJWT
import com.nimbusds.jwt.JWTParser
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
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

    String yamlConfig = """
#tag::yamlconfig[]
micronaut:
  security:
    enabled: true
    token:
      jwt:
        enabled: true
        signatures:
          secret:
            generator: 
              secret: pleaseChangeThisSecretForANewOne #<1>
              jwsAlgorithm: HS256 # <2>
#end::yamlconfig[]
"""

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
                                                                'secret': 'pleaseChangeThisSecretForANewOne',
                                                                'jwsAlgorithm': 'HS256'
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
            'endpoints.beans.enabled': true,
            'endpoints.beans.sensitive': true,
            'pem.path': pemFile.absolutePath,
    ] << flatten(configMap), Environment.TEST)

    @Shared
    @AutoCleanup
    RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    void "test /beans is secured"() {
        when:
        get("/beans")

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.UNAUTHORIZED
    }

    void "/beans can be accessed if authenticated"() {
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
        get("/beans", token)

        then:
        noExceptionThrown()
    }
}
