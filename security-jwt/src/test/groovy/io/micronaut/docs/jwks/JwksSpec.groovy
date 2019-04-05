package io.micronaut.docs.jwks


import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.token.jwt.signature.jwks.JwksSignature
import io.micronaut.testutils.YamlAsciidocTagCleaner
import org.yaml.snakeyaml.Yaml
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class JwksSpec extends Specification implements YamlAsciidocTagCleaner {

    String yamlConfig = """
#tag::yamlconfig[]
micronaut:
  security:
    enabled: true
    token:
      jwt:
        enabled: true
        signatures:
          jwks:
            awscognito: 
              url: 'https://cognito-idp.eu-west-1.amazonaws.com/eu-west-XXXX/.well-known/jwks.json'
#end::yamlconfig[]
"""

    @Shared
    File pemFile = new File('src/test/resources/rsa-2048bit-key-pair.pem')

    @Shared
    Map<String, Object> configMap = [
            'micronaut': [
                    'security': [
                            'enabled': true,
                            'token': [
                                    'jwt': [
                                            'enabled': true,
                                            'signatures': [
                                                    'jwks': [
                                                            'awscognito': [
                                                                    'url': 'https://cognito-idp.eu-west-1.amazonaws.com/eu-west-XXXX/.well-known/jwks.json'
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
            'spec.name': 'docjkwsSpec',
    ] << flatten(configMap), Environment.TEST)

    @Shared
    @AutoCleanup
    HttpClient httpClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

    BlockingHttpClient getClient() {
        httpClient.toBlocking()
    }

    void "JwksSignature bean exists in context"() {
        expect:
        new Yaml().load(cleanYamlAsciidocTag(yamlConfig)) == configMap

        and:
        embeddedServer.applicationContext.containsBean(JwksSignature)
    }
}
