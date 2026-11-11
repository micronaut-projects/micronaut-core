package io.micronaut.http.client.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A pool whose only connection is an HTTP/2 connection that winds down, with no acquire waiting
 * and no other connection, stays registered so that the connection can still be closed with the
 * client, and retires once that connection closes.
 */
class Pool49WindingDownRetirementSpec extends Specification {

    def 'a pool stays registered while its only HTTP/2 connection winds down, and retires once it closes'() {
        given:
        ApplicationContext serverContext = ApplicationContext.run([
                'spec.name'                    : 'Pool49WindingDownRetirementSpec',
                'micronaut.server.port'        : -1,
                'micronaut.server.http-version': '2.0',
        ])
        URL url = serverContext.getBean(EmbeddedServer).start().getURL()
        HoldController controller = serverContext.getBean(HoldController)
        ApplicationContext clientContext = ApplicationContext.run([
                'micronaut.http.client.plaintext-mode': 'h2c_prior_knowledge',
                'micronaut.http.client.read-timeout'  : '10s',
        ])
        DefaultHttpClient client = (DefaultHttpClient) clientContext.createBean(HttpClient, url)

        when: 'a response is held open on the only connection'
        CompletableFuture<String> held = Mono.from(client.retrieve('/pool-retirement/hold')).toFuture()

        then:
        controller.received.await(10, TimeUnit.SECONDS)

        when: 'the connection winds down, and no other request comes'
        client.refresh()
        Map<?, ConnectionManager.PoolHolder> pools = client.connectionManager.pools()

        then: 'the pool stays registered, and still finds the connection to close it'
        new PollingConditions(timeout: 10).eventually {
            assert windingDownConnections(pools) == 1
        }
        pools.size() == 1
        Pool49 pool = (Pool49) pools.values().first().pool
        !pool.retired
        connectionCount(pool) == 1
        !held.done

        when: 'the held response is answered'
        controller.answer.tryEmitValue('held')

        then: 'the connection closes, and the pool retires'
        held.get(10, TimeUnit.SECONDS) == 'held'
        new PollingConditions(timeout: 10).eventually {
            assert pool.retired
            assert pools.isEmpty()
            assert connectionCount(pool) == 0
        }

        cleanup:
        clientContext?.close()
        serverContext?.close()
    }

    private static int windingDownConnections(Map<?, ConnectionManager.PoolHolder> pools) {
        if (pools.size() != 1) {
            return -1
        }
        return ((Pool49) pools.values().first().pool).globalStats.get().windingDownHttp2ConnectionCount()
    }

    private static int connectionCount(Pool49 pool) {
        int count = 0
        pool.forEachConnection { count++ }
        return count
    }

    @Requires(property = 'spec.name', value = 'Pool49WindingDownRetirementSpec')
    @Controller('/pool-retirement')
    static class HoldController {
        final CountDownLatch received = new CountDownLatch(1)
        final Sinks.One<String> answer = Sinks.one()

        @Get(value = '/hold', produces = 'text/plain')
        Mono<String> hold() {
            received.countDown()
            answer.asMono()
        }
    }
}
