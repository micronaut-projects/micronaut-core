package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

/**
 * {@link NettyHttpRequest#getServerName()} must return the host string of the local address
 * without resolving it: a reverse DNS lookup would block the event loop.
 */
class NettyHttpRequestServerNameSpec extends Specification {

    def "serverName is the unresolved host string of the server address"() {
        given:
        def ctx = ApplicationContext.run([
                'spec.name': 'NettyHttpRequestServerNameSpec',
        ])
        def server = ctx.getBean(EmbeddedServer).start()
        def client = ctx.createBean(HttpClient, server.URI)

        when:
        def response = client.toBlocking().retrieve('/server-name').split(' ')

        then:
        // getHostString returns the IP literal of the bound address, getHostName would resolve it (e.g. to 'localhost')
        response[0] == response[1]

        cleanup:
        client.close()
        ctx.close()
    }

    @Controller
    @Requires(property = 'spec.name', value = 'NettyHttpRequestServerNameSpec')
    static class ServerNameController {
        @Get('/server-name')
        String serverName(HttpRequest<?> request) {
            "${request.serverName} ${request.serverAddress.address.hostAddress}"
        }
    }
}
