package io.micronaut.docs.http.server.secondary

import io.micronaut.context.annotation.Property
import io.micronaut.core.util.StringUtils
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Named
import spock.lang.Specification

@MicronautTest
@Property(name = "secondary.enabled", value = StringUtils.TRUE)
class SecondaryServerTest extends Specification {
    // tag::inject[]
    @Client(path = "/", id = SecondaryNettyServer.SERVER_ID)
    @Inject
    HttpClient httpClient // <1>

    @Named(SecondaryNettyServer.SERVER_ID)
    EmbeddedServer embeddedServer // <2>
    // end::inject[]

    void "test secondary server"() {
        given:
        final String result = httpClient.toBlocking().retrieve("/test/secondary/server")

        expect:
        result.endsWith(String.valueOf(embeddedServer.getPort()))
    }

    @Controller("/test/secondary/server")
    static class TestController {
        @Get
        String hello(HttpRequest<?> request) {
            return "Hello from: " + request.getServerAddress().getPort()
        }
    }
}
