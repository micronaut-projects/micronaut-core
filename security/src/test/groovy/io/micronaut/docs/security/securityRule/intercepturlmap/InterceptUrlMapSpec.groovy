package io.micronaut.docs.security.securityRule.intercepturlmap

import io.micronaut.context.ApplicationContext
import io.micronaut.docs.YamlAsciidocTagCleaner
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import org.yaml.snakeyaml.Yaml
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class InterceptUrlMapSpec extends Specification implements YamlAsciidocTagCleaner {

    String yamlConfig = '''\
//tag::yamlconfig[]
micronaut:
  security:
    enabled: true
    interceptUrlMap:
      -
        pattern: /images/*
        httpMethod: GET
        access:
          - isAnonymous() # <1>
      -
        pattern: /books
        access:
          - isAuthenticated() # <2>
      -
        pattern: /books/grails
        httpMethod: GET
        access:
          - ROLE_GRAILS # <3>
          - ROLE_GROOVY
'''//end::yamlconfig[]

    @Shared
    Map<String, Object> ipPatternsMap = ['micronaut': [
            'security': [
                    'enabled'    : true,
                    'interceptUrlMap' : [
                            [
                                    pattern: '/images/*',
                                    httpMethod: 'GET',
                                    access: ['isAnonymous()']
                            ],
                            [
                                    pattern: '/books',
                                    access: ['isAuthenticated()']
                            ],
                            [
                                    pattern: '/books/grails',
                                    httpMethod: 'GET',
                                    access: ['ROLE_GRAILS', 'ROLE_GROOVY']
                            ],
                    ]
            ]
    ]
    ]

    @Shared
    Map<String, Object> config = [
            'spec.name'                                : 'docsintercepturlmap',
            'endpoints.health.enabled'                 : true,
            'endpoints.health.sensitive'               : false,
            'micronaut.security.token.basicAuth.enabled'           : true,
    ] << ipPatternsMap

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, config as Map<String, Object>, "test")

    @Shared
    @AutoCleanup
    RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    void "test accessing a non sensitive endpoint without authentication"() {
        when:
        client.toBlocking().exchange(HttpRequest.GET("/books")
                .basicAuth("user", "password"))

        then:
        noExceptionThrown()

        when:
        client.toBlocking().exchange(HttpRequest.GET("/books/grails")
                .basicAuth("user", "password"))

        then:
        noExceptionThrown()

        when:
        Map m = new Yaml().load(cleanYamlAsciidocTag(yamlConfig))
        then:
        m == ipPatternsMap
    }
}