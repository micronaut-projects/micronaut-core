package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.core.propagation.ThreadPropagatedContextElement
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.http.netty.channel.EventLoopGroupRegistry
import io.netty.channel.EventLoopGroup
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/**
 * The deadlock failure must reach a blocking caller on the client's only event loop even while
 * a thread-bound context (e.g. MDC) is current, instead of being queued on the blocked loop.
 */
class BlockingDeadlockThreadContextSpec extends Specification {

    static final ThreadLocal<String> USER = new ThreadLocal<>()

    static class Mdc implements ThreadPropagatedContextElement<String> {
        @Override
        String updateThreadContext() {
            String old = USER.get()
            USER.set("bob")
            return old
        }

        @Override
        void restoreThreadContext(String oldState) {
            USER.set(oldState)
        }
    }

    @Timeout(30)
    def 'blocking on the same event loop with a thread context fails: #version'() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                'micronaut.netty.event-loops.default.num-threads': 1,
                'micronaut.http.client.allow-block-event-loop'   : true,
                'micronaut.http.client.pool.version'             : version,
                'micronaut.http.client.read-timeout'             : '5s',
        ])
        EventLoopGroup group = ctx.getBean(EventLoopGroupRegistry).getDefaultEventLoopGroup()
        BlockingHttpClient client = ctx.createBean(HttpClient, 'http://localhost:1').toBlocking()

        when:
        group.submit(() -> {
            try (PropagatedContext.Scope ignored = PropagatedContext.empty().plus(new Mdc()).propagate()) {
                client.exchange('/')
            }
        }).get(20, TimeUnit.SECONDS)

        then:
        ExecutionException e = thrown()
        e.cause instanceof HttpClientException
        e.cause.message.contains("deadlock")

        cleanup:
        client.close()
        ctx.close()

        where:
        version << ['V4_0', 'V4_9']
    }
}
