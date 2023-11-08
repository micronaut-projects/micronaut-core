package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.http.netty.channel.EventLoopGroupRegistry
import io.netty.channel.EventLoopGroup
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.ExecutionException

class BlockingDeadlockSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run([
            'micronaut.netty.event-loops.default.num-threads': 1
    ])

    @Shared
    EventLoopGroup group = ctx.getBean(EventLoopGroupRegistry).getDefaultEventLoopGroup()

    @Shared
    @AutoCleanup
    BlockingHttpClient client = ctx.createBean(HttpClient, 'https://micronaut.io').toBlocking()

    def 'blocking on the same event loop should fail: connection already established'() {
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
    }

    def 'blocking on the same event loop should fail: new connection'() {
        when:
        group.submit(() -> {
            client.exchange('/')
        }).get()

        then:
        def e = thrown ExecutionException
        e.cause instanceof HttpClientException
        e.cause.message.contains("deadlock")
    }
}
