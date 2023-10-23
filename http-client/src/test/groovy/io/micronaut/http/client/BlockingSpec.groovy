package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.http.netty.channel.EventLoopGroupRegistry
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Inject
import spock.lang.Specification

import java.util.concurrent.ExecutionException

class BlockingSpec extends Specification {
    def 'blocking on the same event loop should fail: connection already established'() {
        given:
        def ctx = ApplicationContext.run([
                'micronaut.netty.event-loops.default.num-threads': 1,
                // need to disable overzealous "normal" blocking detection to expose this deadlock
                'micronaut.http.client.allow-block-event-loop': true
        ])
        def group = ctx.getBean(EventLoopGroupRegistry).getDefaultEventLoopGroup()
        def client = ctx.createBean(HttpClient, 'https://micronaut.io').toBlocking()

        when:
        // establish pool connection
        client.exchange('/')
        group.submit(() -> {
            client.exchange('/')
        }).get()
        then:
        def e = thrown ExecutionException
        e.cause instanceof HttpClientException
        e.cause.message.contains("deadlock")

        cleanup:
        client.close()
        group.shutdownGracefully()
    }

    def 'blocking on the same event loop should fail: new connection'() {
        given:
        def ctx = ApplicationContext.run([
                'micronaut.netty.event-loops.default.num-threads': 1,
                // need to disable overzealous "normal" blocking detection to expose this deadlock
                'micronaut.http.client.allow-block-event-loop': true
        ])
        def group = ctx.getBean(EventLoopGroupRegistry).getDefaultEventLoopGroup()
        def client = ctx.createBean(HttpClient, 'https://micronaut.io').toBlocking()

        when:
        group.submit(() -> {
            client.exchange('/')
        }).get()
        then:
        def e = thrown ExecutionException
        e.cause instanceof HttpClientException
        e.cause.message.contains("deadlock")

        cleanup:
        client.close()
        group.shutdownGracefully()
    }

    def 'blocking on any event loop should fail'() {
        given:
        def ctx = ApplicationContext.run([
                'spec.name': 'BlockingSpec'
        ])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = ctx.createBean(HttpClient, server.URI).toBlocking()

        when:
        def resp = client.retrieve("/block")
        then:
        resp == "HttpClientException"

        cleanup:
        client.close()
        server.close()
        ctx.close()
    }

    @Controller
    @Requires(property = "spec.name", value = "BlockingSpec")
    static class Ctrl {
        @Inject
        HttpClient client

        @Get("/block")
        @Produces(MediaType.TEXT_PLAIN)
        def block() {
            try {
                client.toBlocking().retrieve("https://micronaut.io")
                return "passed"
            } catch (HttpClientException ignored) {
                return "HttpClientException"
            }
        }
    }
}
