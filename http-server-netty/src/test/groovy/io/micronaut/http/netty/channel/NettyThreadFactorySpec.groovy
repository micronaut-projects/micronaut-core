package io.micronaut.http.netty.channel

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.time.Duration

class NettyThreadFactorySpec extends Specification {
    // this is in http-server-netty so we can use a normal controller

    def 'test reactor blocking detection'(Map<String, Object> config, String expected) {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, config + ['spec.name': "NettyThreadFactorySpec"])
        def client = server.applicationContext.createBean(HttpClient, server.URI).toBlocking()

        expect:
        client.retrieve("/block") == expected

        cleanup:
        client.close()
        server.close()

        where:
        config                                                         | expected
        [:]                                                            | 'blocking prevented'
        ['netty.default-thread-factory-reactor-non-blocking': 'true']  | 'blocking prevented'
        ['netty.default-thread-factory-reactor-non-blocking': 'false'] | 'blocked'
    }

    @Controller
    @Requires(property = "spec.name", value = "NettyThreadFactorySpec")
    static class MyController {
        @Get("/block")
        String block() {
            try {
                return Mono.just("blocked").delayElement(Duration.ofMillis(100)).block()
            } catch (IllegalStateException e) {
                return "blocking prevented"
            }
        }
    }
}
