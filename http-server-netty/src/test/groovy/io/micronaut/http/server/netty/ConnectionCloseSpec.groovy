package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Ignore
import spock.lang.Specification

//ignored because I couldn't reproduce the behavior in #2088
@Ignore
class ConnectionCloseSpec extends Specification {

    void "test the connection header in the response"() {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': ConnectionCloseSpec.simpleName])
        HttpClient client = server.applicationContext.createBean(HttpClient, server.getURL())

        when:
        HttpResponse response = client.toBlocking()
                .exchange(HttpRequest.GET("/connection/close").header(HttpHeaders.CONNECTION, "keep-alive"))

        then:
        response.header(HttpHeaders.CONNECTION) == "close"

        when:
        response = client.toBlocking()
                .exchange(HttpRequest.GET("/connection/default").header(HttpHeaders.CONNECTION, "keep-alive"))

        then:
        response.header(HttpHeaders.CONNECTION) == "keep-alive"

        when:
        response = client.toBlocking()
                .exchange(HttpRequest.GET("/connection/keep-alive").header(HttpHeaders.CONNECTION, "keep-alive"))

        then:
        response.header(HttpHeaders.CONNECTION) == "keep-alive"

        when:
        response = client.toBlocking()
                .exchange(HttpRequest.GET("/connection/close").header(HttpHeaders.CONNECTION, "close"))

        then:
        response.header(HttpHeaders.CONNECTION) == "close"

        when:
        response = client.toBlocking()
                .exchange(HttpRequest.GET("/connection/default").header(HttpHeaders.CONNECTION, "close"))

        then:
        response.header(HttpHeaders.CONNECTION) == "close"

        when:
        response = client.toBlocking()
                .exchange(HttpRequest.GET("/connection/keep-alive").header(HttpHeaders.CONNECTION, "close"))

        then:
        response.header(HttpHeaders.CONNECTION) == "keep-alive"

        when:
        response = client.toBlocking()
                .exchange(HttpRequest.GET("/connection/close"))

        then:
        response.header(HttpHeaders.CONNECTION) == "close"

        when:
        response = client.toBlocking()
                .exchange(HttpRequest.GET("/connection/default"))

        then:
        response.header(HttpHeaders.CONNECTION) == "keep-alive"

        when:
        response = client.toBlocking()
                .exchange(HttpRequest.GET("/connection/keep-alive"))

        then:
        response.header(HttpHeaders.CONNECTION) == "keep-alive"

        cleanup:
        server.close()
    }

    @Controller("/connection")
    static class ConnectionCloseController {

        @Get("/keep-alive")
        HttpResponse keepAlive() {
            HttpResponse.ok().header(HttpHeaders.CONNECTION, "keep-alive")
        }

        @Get("/default")
        HttpResponse noHeader() {
            HttpResponse.ok()
        }

        @Get("/close")
        HttpResponse close() {
            HttpResponse.ok().header(HttpHeaders.CONNECTION, "close")
        }
    }
}
