package io.micronaut.docs.server.filters

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.docs.server.intro.HelloControllerSpec
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class SecurityHeaderPopulatorSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer =
            ApplicationContext.run(EmbeddedServer, ['spec.name': HelloControllerSpec.simpleName,
                                                     'spec.filter': 'SecurityHeaderPopulator',
                                                     'spec.lang': 'groovy'], Environment.TEST)
    @Shared @AutoCleanup HttpClient httpClient =
            embeddedServer.getApplicationContext()
                    .createBean(HttpClient, embeddedServer.getURL())

    void "test security header populator"() {
        given:
        def response = httpClient.toBlocking().exchange(HttpRequest.GET('/hello'))

        expect:
        response.headers.get('X-Content-Type-Options') == 'nosniff'
    }
}
