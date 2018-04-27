package io.micronaut.docs.security.securityRule

import io.micronaut.context.ApplicationContext
import io.micronaut.docs.YamlAsciidocTagCleaner
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.authorization.AuthorizationUtils
import org.yaml.snakeyaml.Yaml
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class InterceptUrlMapSpec extends Specification implements AuthorizationUtils, YamlAsciidocTagCleaner {

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
            'endpoints.health.enabled'                 : true,
            'endpoints.health.sensitive'               : false,
            'micronaut.security.token.jwt.enabled'           : true,
            'micronaut.security.token.jwt.generator.signature.enabled': true,
            'micronaut.security.token.jwt.generator.signature.secret': 'qrD6h8K6S9503Q06Y6Rfk21TErImPYqa',
    ] << ipPatternsMap

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, config as Map<String, Object>, "test")

    @Shared
    @AutoCleanup
    RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    void "test accessing a non sensitive endpoint without authentication"() {
        when:
        get("/books/grails")

        then:
        noExceptionThrown()

        when:
        get("/books")

        then:
        noExceptionThrown()

        when:
        Map m = new Yaml().load(cleanYamlAsciidocTag(yamlConfig))
        then:
        m == ipPatternsMap
    }
}