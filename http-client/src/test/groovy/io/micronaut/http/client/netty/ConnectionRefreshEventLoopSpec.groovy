package io.micronaut.http.client.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class ConnectionRefreshEventLoopSpec extends Specification {

    def 'refreshing the client from another thread winds idle connections down on their event loop'() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name': 'ConnectionRefreshEventLoopSpec',
        ])
        DefaultHttpClient client = (DefaultHttpClient) server.applicationContext.createBean(HttpClient, server.URL)

        when:
        def first = client.toBlocking().retrieve('/refresh-loop/hello')
        // the test thread is not an event loop
        client.connectionManager().refresh()
        def second = client.toBlocking().retrieve('/refresh-loop/hello')
        then:
        first == 'hello'
        second == 'hello'

        cleanup:
        client.close()
        server.close()
    }

    @Controller('/refresh-loop')
    @Requires(property = 'spec.name', value = 'ConnectionRefreshEventLoopSpec')
    static class RefreshController {
        @Get('/hello')
        String hello() {
            'hello'
        }
    }
}
