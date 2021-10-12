package io.micronaut.http.server.netty


import io.micronaut.context.annotation.Property
import io.micronaut.core.util.StringUtils
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration
import io.micronaut.runtime.ApplicationConfiguration
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Named
import spock.lang.Specification

@MicronautTest
@Property(name = "NettyMultiServerSpec", value = StringUtils.TRUE)
class NettyMultiServerSpec extends Specification {

    @Inject ApplicationConfiguration applicationConfiguration
    @Inject NettyEmbeddedServerFactory embeddedServerFactory
    @Inject NettyHttpServerConfiguration configuration
    @Inject EmbeddedServer primaryServer
    @Named("secondary")
    @Inject EmbeddedServer secondaryServer
    @Inject @Client("/") HttpClient primaryClient

    void "test start multiple servers"() {
        given:
        def secondaryClient = secondaryServer.applicationContext.createBean(HttpClient, secondaryServer.getURI())
        def secondaryResponse = secondaryClient.toBlocking().retrieve("/test/secondary/server")
        def primaryResponse = primaryClient.toBlocking().retrieve('/test/secondary/server')

        expect:
        secondaryServer.isRunning()
        secondaryServer.getPort() > 0
        primaryServer.getPort() != secondaryServer.getPort()
        primaryResponse != secondaryResponse
        secondaryResponse == "port: $secondaryServer.port"
        primaryResponse == "port: $primaryServer.port"

        cleanup:
        secondaryClient.close()
    }

    @Controller("/test/secondary/server")
    static class TestController {
        @Get("/")
        String test(HttpRequest<?> request) {
            return "port: $request.serverAddress.port"
        }
    }
}
