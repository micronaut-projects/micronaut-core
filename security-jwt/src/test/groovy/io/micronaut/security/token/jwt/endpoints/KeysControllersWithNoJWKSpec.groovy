package io.micronaut.security.token.jwt.endpoints

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class KeysControllersWithNoJWKSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'micronaut.security.enabled': true,
            'micronaut.security.token.jwt.enabled': true,
            'micronaut.security.endpoints.keys.enabled': true
    ])

    @Shared
    @AutoCleanup
    RxHttpClient httpClient = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    BlockingHttpClient getClient() {
        httpClient.toBlocking()
    }

    void "keys JSON Object MUST have a keys member"() {
        when:
        String keysJson = client.retrieve(HttpRequest.GET("/keys"), String)

        then:
        keysJson == '{"keys":[]}'
    }
}
