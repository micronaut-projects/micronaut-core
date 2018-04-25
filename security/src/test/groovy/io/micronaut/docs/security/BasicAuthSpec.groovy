package io.micronaut.docs.security

import io.micronaut.context.ApplicationContext
import io.micronaut.docs.YamlAsciidocTagCleaner
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.authorization.AuthorizationUtils
import org.yaml.snakeyaml.Yaml
import spock.lang.AutoCleanup
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

@Ignore
class BasicAuthSpec extends Specification implements YamlAsciidocTagCleaner, AuthorizationUtils {

    String yamlConfig = '''\
//tag::yamlconfig[]
micronaut:
  security:
    enabled: true
    basicAuth:
      enabled: true
'''//end::yamlconfig[]

    @Shared
    Map<String, Object> confMap = [
            "micronaut": [
                    "security": [
                            "enabled"    : true,
                            "basicAuth" : [
                                    enabled: true,
                            ],
                    ]
            ]
    ]

    @Shared
    Map<String, Object> config = [
            'spec.name' : 'basicauth',
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
        get("/health", token, 'Basic')

        then:
        noExceptionThrown()
    }
}
