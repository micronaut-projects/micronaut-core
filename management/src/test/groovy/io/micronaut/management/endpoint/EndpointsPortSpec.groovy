package io.micronaut.management.endpoint

import io.micronaut.context.ApplicationContext
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Retry
import spock.lang.Specification


class EndpointsPortSpec extends Specification {
    // retry as a port binding conflict on CI can occur
    @Retry
    def "test that it is possible to change admin port"() {

        given:
        def port = SocketUtils.findAvailableTcpPort()
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name': getClass().simpleName,
                'endpoints.all.port': port,
                'endpoints.all.enabled': true
        ])
        HttpClient rxClient = server.applicationContext.createBean(HttpClient.class, new URL("http://localhost:$port"))
        HttpClient serverClient = server.applicationContext.createBean(HttpClient.class, server.getURL())

        when:
        rxClient.toBlocking().retrieve('/health')

        then:
        noExceptionThrown()

        when:
        serverClient.toBlocking().retrieve('/health')

        then:
        def e = thrown(HttpClientResponseException)

        when:
        def response = e.response

        then:
        response.status == HttpStatus.NOT_FOUND

        cleanup:
        serverClient.close()
        rxClient.close()
        server.close()
    }
}
