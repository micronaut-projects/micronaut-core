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
package io.micronaut.docs.security.token.propagation

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.docs.YamlAsciidocTagCleaner
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.token.propagation.TokenPropagationHttpClientFilter
import io.micronaut.security.token.writer.HttpHeaderTokenWriterConfiguration
import io.micronaut.security.token.writer.HttpHeaderTokenWriterConfigurationProperties
import io.micronaut.security.token.writer.TokenWriter
import org.yaml.snakeyaml.Yaml
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class TokenPropagationSpec extends Specification implements YamlAsciidocTagCleaner {

    String yamlConfig = '''\
//tag::yamlconfig[]
micronaut:
    application:
        name: gateway
    security:
        enabled: true
        token:
            jwt:
                enabled: true
                signatures:
                    secret:
                        generator:
                            secret: "pleaseChangeThisSecretForANewOne"
                            jwsAlgorithm: HS256                
            writer:
                header:
                    enabled: true
                    headerName: "Authorization"
                    prefix: "Bearer "
            propagation:
                enabled: true
                serviceIdRegex: "http://localhost:(8083|8081|8082)"                            
'''//end::yamlconfig[]





    @Shared
    Map<String, Object> propagationMap = [
            'micronaut': [
                    'application': [
                            'name': 'gateway',
                    ],
                    'security': [
                            'enabled': true,
                            'token': [
                                    'jwt': [
                                            'enabled': true,
                                            'signatures': [
                                                    'secret': [
                                                        'generator': [
                                                                'secret': "pleaseChangeThisSecretForANewOne",
                                                                'jwsAlgorithm': 'HS256'
                                                        ]
                                                    ]
                                            ]
                                    ],
                                    'writer': [
                                            'header': [
                                                    'enabled': true,
                                                    'headerName': 'Authorization',
                                                    'prefix': 'Bearer ',
                                            ]
                                    ],
                                    'propagation': [
                                            'enabled': true,
                                            'serviceIdRegex': 'http://localhost:(8083|8081|8082)'
                                    ]
                            ]
                    ]
            ]
    ]

    @Shared
    Map<String, Object> config = [
            'spec.name'                 : TokenPropagationSpec.class.simpleName
    ] << flatten(propagationMap)

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
            config as Map<String, Object>,
            Environment.TEST)

    @Shared
    @AutoCleanup
    RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    void "test valid propagation configuration"() {
        when:
        embeddedServer.applicationContext.getBean(TokenPropagationHttpClientFilter)

        then:
        noExceptionThrown()

        when:
        embeddedServer.applicationContext.getBean(TokenWriter)

        then:
        noExceptionThrown()

        when:
        embeddedServer.applicationContext.getBean(HttpHeaderTokenWriterConfiguration)

        then:
        noExceptionThrown()

        when:
        Map m = new Yaml().load(cleanYamlAsciidocTag(yamlConfig))
        then:
        m == propagationMap
    }
}