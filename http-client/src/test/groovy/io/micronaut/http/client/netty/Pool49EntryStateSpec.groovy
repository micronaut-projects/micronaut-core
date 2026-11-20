package io.micronaut.http.client.netty

import io.micronaut.http.client.HttpClientConfiguration
import io.netty.channel.DefaultEventLoopGroup
import io.netty.channel.EventLoop
import org.slf4j.LoggerFactory
import spock.lang.AutoCleanup
import spock.lang.Specification

import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

/**
 * Each lifecycle step of a pool entry moves its connection between the pool's connection counts
 * exactly once, so that an entry that has closed is no longer counted anywhere.
 */
class Pool49EntryStateSpec extends Specification {

    @AutoCleanup('shutdownGracefully')
    DefaultEventLoopGroup group = new DefaultEventLoopGroup(1)
    EventLoop loop = group.next()
    Pool49 pool = new Pool49(null, LoggerFactory.getLogger(Pool49EntryStateSpec), new HttpClientConfiguration.ConnectionPoolConfiguration(), group)
    Pool49.LocalPoolPair pair = pool.localPools[0]

    def setup() {
        pool.pickPreferredPoolOverride = { pools -> pools[0] }
        // an acquire that is never dispatched keeps the pool from retiring, which would need a listener
        pool.createPendingRequest(null)
    }

    def 'an HTTP/2 connection that is established, winds down and closes is counted in one state at a time'() {
        given:
        Pool.Http2PoolEntry entry = onLoop { reserveConnection(); pool.createHttp2PoolEntry(loop, null) }

        expect:
        counts() == [pending: 1, http1: 0, http2: 0, windingDown: 0]

        when:
        onLoop { entry.onConnectionEstablished(0) }

        then:
        counts() == [pending: 0, http1: 0, http2: 1, windingDown: 0]
        pair.http2.connections.contains(entry)

        when: 'it winds down twice'
        onLoop { entry.markUnavailable(); entry.markUnavailable() }

        then: 'it moved once'
        counts() == [pending: 0, http1: 0, http2: 0, windingDown: 1]
        pair.http2.connections.contains(entry)

        when: 'it becomes inactive twice'
        onLoop { entry.onConnectionInactive(); entry.onConnectionInactive() }

        then:
        counts() == [pending: 0, http1: 0, http2: 0, windingDown: 0]
        !pair.http2.connections.contains(entry)
    }

    def 'an HTTP/2 connection that becomes inactive while it takes requests is no longer counted'() {
        given:
        Pool.Http2PoolEntry entry = onLoop { reserveConnection(); pool.createHttp2PoolEntry(loop, null) }
        onLoop { entry.onConnectionEstablished(0) }

        when:
        onLoop { entry.onConnectionInactive() }

        then:
        counts() == [pending: 0, http1: 0, http2: 0, windingDown: 0]
        !pair.http2.connections.contains(entry)
    }

    def 'an HTTP/2 connection that winds down before it is established is counted as winding down once it is'() {
        given:
        Pool.Http2PoolEntry entry = onLoop { reserveConnection(); pool.createHttp2PoolEntry(loop, null) }

        when:
        onLoop { entry.markUnavailable() }

        then: 'it is still a pending connection'
        counts() == [pending: 1, http1: 0, http2: 0, windingDown: 0]
        !pair.http2.connections.contains(entry)

        when:
        onLoop { entry.onConnectionEstablished(10) }

        then: 'it takes no requests'
        counts() == [pending: 0, http1: 0, http2: 0, windingDown: 1]
        pair.http2.connections.contains(entry)
        pair.http2.firstAvailable == null

        when:
        onLoop { entry.onConnectionInactive() }

        then:
        counts() == [pending: 0, http1: 0, http2: 0, windingDown: 0]
        !pair.http2.connections.contains(entry)
    }

    def 'an HTTP/1 connection that is established and closes is counted in one state at a time'() {
        given:
        Pool.Http1PoolEntry entry = onLoop { reserveConnection(); pool.createHttp1PoolEntry(loop, null) }

        when:
        onLoop { entry.onConnectionEstablished(); entry.onConnectionEstablished() }

        then:
        counts() == [pending: 0, http1: 1, http2: 0, windingDown: 0]
        pair.http1.firstAvailable == entry

        when: 'it becomes inactive twice'
        onLoop { entry.onConnectionInactive(); entry.onConnectionInactive() }

        then:
        counts() == [pending: 0, http1: 0, http2: 0, windingDown: 0]
        !pair.http1.connections.contains(entry)
        pair.http1.firstAvailable == null
    }

    private void reserveConnection() {
        assert pool.openConnectionStep1()
        pair.openConnectionStep2()
    }

    private Map<String, Integer> counts() {
        def stats = pool.globalStats.get()
        assert !stats.retired()
        return [
                pending    : stats.pendingConnectionCount(),
                http1      : stats.http1ConnectionCount(),
                http2      : stats.http2ConnectionCount(),
                windingDown: stats.windingDownHttp2ConnectionCount(),
        ]
    }

    private <T> T onLoop(Closure<T> task) {
        return loop.submit(task as Callable<T>).get(10, TimeUnit.SECONDS)
    }
}
