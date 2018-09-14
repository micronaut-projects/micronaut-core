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
package io.micronaut.docs.security.token.basicauth

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.docs.YamlAsciidocTagCleaner
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import org.yaml.snakeyaml.Yaml
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class BasicAuthSpec extends Specification implements YamlAsciidocTagCleaner {

    String yamlConfig = '''\
//tag::yamlconfig[]
micronaut:
  security:
    enabled: true
'''//end::yamlconfig[]

    @Shared
    Map<String, Object> confMap = [
            'micronaut': [
                    'security': [
                            'enabled'    : true
                    ]
            ]
    ]

    @Shared
    Map<String, Object> config = [
            'spec.name' : 'docsbasicauth',
            'endpoints.beans.enabled'                 : true,
            'endpoints.beans.sensitive'               : true,
    ] << flatten(confMap)

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, config as Map<String, Object>, Environment.TEST)

    @Shared
    @AutoCleanup
    RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    void "test /beans is secured but accesible if you supply valid credentials with Basic Auth"() {
        when:
        Map m = new Yaml().load(cleanYamlAsciidocTag(yamlConfig))

        then:
        m == confMap

        when:
        String token = 'dXNlcjpwYXNzd29yZA==' // user:passsword Base64
        client.toBlocking().exchange(HttpRequest.GET("/beans")
                .header("Authorization", "Basic ${token}".toString()), String)

        then:
        noExceptionThrown()
    }
}
