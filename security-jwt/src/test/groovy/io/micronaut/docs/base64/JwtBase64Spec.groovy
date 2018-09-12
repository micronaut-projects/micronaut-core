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
package io.micronaut.docs.base64

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
import io.micronaut.security.token.jwt.signature.SignatureConfiguration
import org.yaml.snakeyaml.Yaml
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class JwtBase64Spec extends Specification implements AuthorizationUtils, YamlAsciidocTagCleaner {

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
              secret: 'cGxlYXNlQ2hhbmdlVGhpc1NlY3JldEZvckFOZXdPbmU=' #<1>
              base64: true #<2>
              jwsAlgorithm: HS256
#end::yamlconfig[]
"""

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
                                                                'secret': 'cGxlYXNlQ2hhbmdlVGhpc1NlY3JldEZvckFOZXdPbmU=',
                                                                'base64': true,
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
            'spec.name': 'base64',
            'endpoints.beans.enabled': true,
            'endpoints.beans.sensitive': true,
    ] << flatten(configMap), Environment.TEST)

    @Shared
    @AutoCleanup
    RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    void "a JWT signed with HS256 with a base 64 encoded secret can be used to acccess a micronaut app secured with the same base64 encoded secret"() {
        expect:
        new Yaml().load(cleanYamlAsciidocTag(yamlConfig)) == configMap
        new String(configMap.micronaut.security.token.jwt.signatures.secret.generator.secret.decodeBase64()) == 'pleaseChangeThisSecretForANewOne'
        embeddedServer.applicationContext.getBean(SignatureConfiguration.class)
        embeddedServer.applicationContext.getBean(SignatureConfiguration.class, Qualifiers.byName("generator"))
        embeddedServer.applicationContext.getBean(TokenGenerator.class)

        when:
        /*
        https://jwt.io
        HEADER:ALGORITHM & TOKEN TYPE

                {
                    "alg": "HS256",
                    "typ": "JWT"
                }
        PAYLOAD:DATA

                {
                    "sub": "1234567890",
                    "name": "John Doe",
                    "iat": 1516239022
                }
        VERIFY SIGNATURE
        HMACSHA256(
            base64UrlEncode(header) + "." +
            base64UrlEncode(payload),
            cGxlYXNlQ2hhbmdlVGhpc1NlY3JldEZvckFOZXdPbmU=
        ) secret base64 encoded
       */
        String token = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.6cD3MnZmX2xyEAWyh-GgGD11TX8SmvmHVLknuAIJ8yE'
        get("/beans", token)

        then:
        noExceptionThrown()

        when: 'if the token uses a plain secret instead of the base64 encoded secret in the signature'
        token = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.8YXijqm04ZfCMUKyG3F4TXdMC94bOHYDUzXJwCfB7xE'
        get("/beans", token)

        then: 'access is not granted'
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.UNAUTHORIZED
    }
}
