package io.micronaut.docs.security.securityRule.builtinendpoints

import io.micronaut.context.ApplicationContext
import io.micronaut.docs.YamlAsciidocTagCleaner
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import org.yaml.snakeyaml.Yaml
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class BuiltInEndpointsSpec extends Specification implements YamlAsciidocTagCleaner {

    String yamlConfig = '''\
//tag::yamlconfig[]
endpoints:
  beans:
    enabled: true
    sensitive: true # <1>
  info:
    enabled: true
    sensitive: false  # <2>
'''//end::yamlconfig[]

    @Shared
    Map<String, Object> endpointsMap = [
            endpoints: [
                    beans: [
                            enabled                : true,
                            sensitive              : true,
                    ],
                    info: [
                            enabled                : true,
                            sensitive              : false,
                    ],
            ]
    ]

    @Shared
    Map<String, Object> config = [
            'spec.name': 'docbuiltinendpoints',
            'micronaut.security.enabled': true,
            'micronaut.security.token.enabled': true,
    ] << flatten(endpointsMap)

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, config as Map<String, Object>, "test")

    @Shared
    @AutoCleanup
    RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    void "test accessing a non sensitive endpoint without authentication"() {
        when:
        client.toBlocking().exchange(HttpRequest.GET("/info"))

        then:
        noExceptionThrown()

        when:
        Map m = new Yaml().load(cleanYamlAsciidocTag(yamlConfig))
        then:
        m == endpointsMap
    }

    void "test accessing a sensitive endpoint requires authentication"() {
        when:
        client.toBlocking().exchange(HttpRequest.GET("/beans"))

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.UNAUTHORIZED

        when:
        client.toBlocking().exchange(HttpRequest.GET("/beans").basicAuth("user", "password"))

        then:
        noExceptionThrown()

        when:
        Map m = new Yaml().load(cleanYamlAsciidocTag(yamlConfig))
        then:
        m == endpointsMap
    }
}