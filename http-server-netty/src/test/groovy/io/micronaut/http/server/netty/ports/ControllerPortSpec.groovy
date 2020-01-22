package io.micronaut.http.server.netty.ports

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Retry
import spock.lang.Specification

class ControllerPortSpec extends Specification {


    @Retry // try because a port binding issue could occur on CI
    void "test custom controller port"() {

        given:
        def customPort = SocketUtils.findAvailableTcpPort()
        EmbeddedServer embeddedServer = ApplicationContext.run(
                EmbeddedServer,
                ['my.controller.port': customPort]
        )
        def client = embeddedServer.applicationContext.createBean(RxHttpClient, new URL("http://localhost:$customPort"))
        def client2 = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        def response = client.toBlocking().retrieve("/custom-port1")

        then:
        response == 'ok'

        when:
        client2.toBlocking().retrieve("/custom-port1")

        then:
        def e = thrown(HttpClientResponseException)
        e.response.status() == HttpStatus.NOT_FOUND


        cleanup:
        embeddedServer.close()
        client.close()
        client2.close()
    }

    @Requires(property = "my.controller.port")
    @Controller(value = "/custom-port1", port='${my.controller.port:9999}')
    static class OneController {

        @Get("/")
        String test() {
            return "ok"
        }
    }
}
