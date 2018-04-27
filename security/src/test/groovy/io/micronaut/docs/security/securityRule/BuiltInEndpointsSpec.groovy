package io.micronaut.docs.security.securityRule

import io.micronaut.context.ApplicationContext
import io.micronaut.docs.YamlAsciidocTagCleaner
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.authorization.AuthorizationUtils
import org.yaml.snakeyaml.Yaml
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class BuiltInEndpointsSpec extends Specification implements AuthorizationUtils, YamlAsciidocTagCleaner {

    String yamlConfig = '''\
//tag::yamlconfig[]
endpoints:
  beans:
    enabled: true
    sensitive: true # <1>
  health:
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
                    health: [
                            enabled                : true,
                            sensitive              : false,
                    ],
            ]
    ]

    @Shared
    Map<String, Object> config = [
            'micronaut.security.endpoints.login': true,
            'spec.authentication': true,
            'micronaut.security.enabled': true,
            'micronaut.security.token.enabled': true,
            'micronaut.security.token.jwt.enabled': true,
            'micronaut.security.token.jwt.generator.signature.enabled': true,
            'micronaut.security.token.jwt.generator.signature.secret': 'qrD6h8K6S9503Q06Y6Rfk21TErImPYqa',
    ] << flatten(endpointsMap)

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, config as Map<String, Object>, "test")

    @Shared
    @AutoCleanup
    RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    void "test accessing a non sensitive endpoint without authentication"() {
        when:
        get("/health")

        then:
        noExceptionThrown()

        when:
        Map m = new Yaml().load(cleanYamlAsciidocTag(yamlConfig))
        then:
        m == endpointsMap
    }

    void "test accessing a sensitive endpoint requires authentication"() {
        when:
        get("/beans")

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.UNAUTHORIZED

        when:
        String token = loginWith('valid')
        get("/beans", token)

        then:
        noExceptionThrown()

        when:
        Map m = new Yaml().load(cleanYamlAsciidocTag(yamlConfig))
        then:
        m == endpointsMap
    }
}