package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.http.netty.channel.EventLoopGroupRegistry
import io.netty.channel.EventLoopGroup
import spock.lang.AutoCleanup
import spock.lang.Retry
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.ExecutionException

@Retry
class BlockingDeadlockSpec extends Specification {
    @Shared
    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run([
            'micronaut.netty.event-loops.default.num-threads': 1,
            // need to disable overzealous "normal" blocking detection to expose this deadlock
            'micronaut.http.client.allow-block-event-loop': true
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
        ExecutionException e = thrown()
        e.cause instanceof HttpClientException
        e.cause.message.contains("deadlock")
    }

    def 'blocking on the same event loop should fail: new connection'() {
        when:
        group.submit(() -> {
            client.exchange('/')
        }).get()

        then:
        ExecutionException e = thrown()
        e.cause instanceof HttpClientException
        e.cause.message.contains("deadlock")
    }
}
