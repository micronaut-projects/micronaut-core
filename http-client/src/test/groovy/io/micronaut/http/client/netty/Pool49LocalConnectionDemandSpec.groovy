package io.micronaut.http.client.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpResponse
import io.netty.channel.ChannelFuture
import io.netty.channel.EventLoop
import io.netty.channel.embedded.EmbeddedChannel
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

/**
 * With enforced connection locality, requests that wait on one event loop must keep getting new
 * connections as the pending connection limit frees up, not only the first one after it.
 */
class Pool49LocalConnectionDemandSpec extends Specification {

    static final int REQUESTS = 10

    ApplicationContext ctx
    DefaultHttpClient client
    ConnectionManagerSpec.EmbeddedTestConnectionHttp1 conn
    EmbeddedConnectionManager connectionManager

    def setup() {
        ctx = ApplicationContext.run([
                'micronaut.http.client.pool.max-pending-connections': 4,
                // no protocol is known before a connection is established: the lesser of both limits applies
                'micronaut.http.client.pool.max-concurrent-http2-connections': 100,
                'micronaut.http.client.pool.connection-locality'    : 'enforced-always',
        ])
        client = ctx.getBean(DefaultHttpClient)
        conn = new ConnectionManagerSpec.EmbeddedTestConnectionHttp1()
        conn.setupHttp1()
        conn.clientChannel = new EmbeddedChannel(new DummyChannelId('client'), conn.clientInitializer)
        // every connection attempt lands on the same event loop, and none completes on its own
        List<ChannelFuture> openFutures = (1..REQUESTS).collect { (ChannelFuture) conn.clientChannel.newPromise() }
        connectionManager = new EmbeddedConnectionManager(client.connectionManager, [conn.clientChannel] * REQUESTS, openFutures)
        client.connectionManager = connectionManager
    }

    def cleanup() {
        client.close()
        ctx.close()
    }

    def 'each connection that opens lets one more connection open for the requests still waiting on its loop'() {
        given: 'more requests than pending connections, all on one loop'
        List<CompletableFuture<HttpResponse<?>>> responses = (1..REQUESTS).collect {
            def response = Mono.from(client.exchange(conn.scheme + '://example.com/foo')).toFuture()
            conn.advance()
            response
        }
        Pool49 pool = pool()
        EventLoop loop = conn.clientChannel.eventLoop()

        expect: 'only as many connections as may be pending are opened'
        connectionManager.i == 4

        when: 'one of them is established'
        def first = pool.createHttp1PoolEntry(loop, null)
        first.onConnectionEstablished()
        conn.advance()

        then: 'another one opens in its place'
        connectionManager.i == 5

        when: 'a second one is established'
        def second = pool.createHttp1PoolEntry(loop, null)
        second.onConnectionEstablished()
        conn.advance()

        then: 'another one opens in its place too'
        connectionManager.i == 6

        cleanup:
        responses*.cancel(true)
        // the entries stand for connections that do not exist: the client cannot close them
        first?.onConnectionInactive()
        second?.onConnectionInactive()
    }

    private Pool49 pool() {
        def field = ConnectionManager.getDeclaredField('pools')
        field.accessible = true
        Map<?, ConnectionManager.PoolHolder> pools = field.get(connectionManager)
        assert pools.size() == 1
        return (Pool49) pools.values().first().pool
    }
}
