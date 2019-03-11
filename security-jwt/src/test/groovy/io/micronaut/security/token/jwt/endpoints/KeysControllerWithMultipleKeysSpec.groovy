package io.micronaut.security.token.jwt.endpoints

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class KeysControllerWithMultipleKeysSpec extends Specification {

    @Shared
    Map<String, Object> conf = [
            'micronaut.security.enabled': true,
            'micronaut.security.endpoints.keys.enabled': true,
            'micronaut.security.token.jwt.enabled': true,
            'spec.name': 'keyscontrollerwithmultiplekeys'
    ]

    @AutoCleanup
    @Shared
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, conf)

    @AutoCleanup
    @Shared
    HttpClient httpClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.URL)

    BlockingHttpClient getClient() {
        httpClient.toBlocking()
    }

    void "if two jwk providers the JSON returned by /keys endpoint contains a single keys key with two entries"() {
        expect:
        embeddedServer.applicationContext.containsBean(MockJwkProviderOne)
        embeddedServer.applicationContext.containsBean(MockJwkProviderTwo)

        when:
        Collection<JwkProvider> jwkProviders = embeddedServer.applicationContext.getBeansOfType(JwkProvider)

        then:
        jwkProviders.size() == 2

        when:
        Map json = client.retrieve(HttpRequest.GET('/keys'), Map)

        then:
        json.containsKey('keys')

        and:
        json['keys'].size() == 2

        and:
        json['keys'].collect { it['kid'] as Integer }.sort() == [1, 2]
    }
}
