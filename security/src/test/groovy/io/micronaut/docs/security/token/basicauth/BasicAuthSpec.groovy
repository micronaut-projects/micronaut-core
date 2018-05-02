package io.micronaut.docs.security.token.basicauth

import io.micronaut.context.ApplicationContext
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
    token:
      basicAuth:
        enabled: true
'''//end::yamlconfig[]

    @Shared
    Map<String, Object> confMap = [
            'micronaut': [
                    'security': [
                            'enabled'    : true,
                            'token': [
                                    'basicAuth' : [
                                            enabled: true,
                                    ],
                            ],
                    ]
            ]
    ]

    @Shared
    Map<String, Object> config = [
            'spec.name' : 'docsbasicauth',
            'endpoints.health.enabled'                 : true,
            'endpoints.health.sensitive'               : true,
    ] << flatten(confMap)

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, config as Map<String, Object>, "test")

    @Shared
    @AutoCleanup
    RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    void "test /health is secured but accesible if you supply valid credentials with Basic Auth"() {
        when:
        Map m = new Yaml().load(cleanYamlAsciidocTag(yamlConfig))

        then:
        m == confMap

        when:
        String token = 'dXNlcjpwYXNzd29yZA==' // user:passsword Base64
        client.toBlocking().exchange(HttpRequest.GET("/health")
                .header("Authorization", "Basic ${token}".toString()), String)

        then:
        noExceptionThrown()
    }
}
