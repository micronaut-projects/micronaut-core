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

class IpPatternSpec extends Specification implements AuthorizationUtils, YamlAsciidocTagCleaner {

    String yamlConfig = '''\
//tag::yamlconfig[]
micronaut:
  security:
    enabled: true
    ipPatterns:
      - 127.0.0.1
      - 192.168.1.*
'''//end::yamlconfig[]


    @Shared
    Map<String, Object> ipPatternsMap = ["micronaut": [
            "security": [
                    "enabled"    : true,
                    "ipPatterns" : ['127.0.0.1', '192.168.1.*']
            ]
        ]
    ]

    @Shared
    Map<String, Object> config = [
            'endpoints.health.enabled'                 : true,
            'endpoints.health.sensitive'               : false,
            "micronaut.security.token.signature.secret": 'qrD6h8K6S9503Q06Y6Rfk21TErImPYqa',
    ] << flatten(ipPatternsMap)

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, config as Map<String, Object>, "test")

    @Shared
    @AutoCleanup
    RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    void "test accessing a resource from a whitelisted IP is successful"() {
        when:
        get("/health")

        then:
        noExceptionThrown()

        when:
        Map m = new Yaml().load(cleanYamlAsciidocTag(yamlConfig))
        then:
        m == ipPatternsMap
    }
}