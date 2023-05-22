package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.http.netty.channel.EventLoopGroupRegistry
import spock.lang.Specification

import java.util.concurrent.ExecutionException

class BlockingDeadlockSpec extends Specification {
    def 'blocking on the same event loop should fail: connection already established'() {
        given:
        def ctx = ApplicationContext.run([
                'micronaut.netty.event-loops.default.num-threads': 1
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
                'micronaut.netty.event-loops.default.num-threads': 1
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
}
