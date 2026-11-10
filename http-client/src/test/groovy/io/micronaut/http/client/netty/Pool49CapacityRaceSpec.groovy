package io.micronaut.http.client.netty

import io.micronaut.http.client.HttpClientConfiguration
import io.netty.channel.DefaultEventLoopGroup
import io.netty.channel.EventLoop
import org.slf4j.LoggerFactory
import spock.lang.Specification

import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * An acquire that finds the connection limit hit, and is queued only after the capacity was
 * freed, must still get a new connection: freeing the capacity looked for a waiting request
 * before this one was queued.
 */
class Pool49CapacityRaceSpec extends Specification {

    def 'an acquire queued just after the only HTTP/2 connection winds down gets a new connection (#queue queue)'() {
        given: 'a limit of one HTTP/2 connection, which is open and has no free stream'
        AtomicReference<Runnable> duringLimitCheck = new AtomicReference<>()
        def config = new HttpClientConfiguration.ConnectionPoolConfiguration() {
            @Override
            int getMaxConcurrentHttp2Connections() {
                // runs between reading the connection count and queueing the request
                duringLimitCheck.getAndSet(null)?.run()
                return 1
            }
        }
        config.connectionLocality = locality
        def group = new DefaultEventLoopGroup(1)
        EventLoop loop = group.next()
        // without a listener, the connection the pool opens fails at once, which fails the
        // request waiting for it: what matters here is that one is opened at all
        def pool = new Pool49(null, LoggerFactory.getLogger(Pool49CapacityRaceSpec), config, group)
        def pair = pool.localPools[0]
        Pool.Http2PoolEntry entry = onLoop(loop) {
            assert pool.openConnectionStep1()
            pair.openConnectionStep2()
            def e = pool.createHttp2PoolEntry(loop, null)
            e.onConnectionEstablished(0)
            pair.needPendingConnection = false
            return e
        }

        when: 'it winds down while a new acquire checks the limit'
        duringLimitCheck.set { entry.markUnavailable() }
        pool.pickPreferredPoolOverride = { pools -> preferred ? pools[0] : null }
        def request = pool.createPendingRequest(null)
        CompletableFuture<?> acquired = request.flow().toCompletableFuture()
        onLoop(loop) { request.dispatch() }
        onLoop(loop) {}
        onLoop(loop) {}

        then: 'a connection is opened for the queued acquire'
        duringLimitCheck.get() == null
        acquired.isCompletedExceptionally()

        cleanup:
        group.shutdownGracefully(0, 0, TimeUnit.SECONDS)

        where:
        queue    | preferred | locality
        'global' | false     | HttpClientConfiguration.ConnectionPoolConfiguration.ConnectionLocality.PREFERRED
        'local'  | true      | HttpClientConfiguration.ConnectionPoolConfiguration.ConnectionLocality.ENFORCED_ALWAYS
    }

    private static <T> T onLoop(EventLoop loop, Closure<T> task) {
        return loop.submit(task as Callable<T>).get(10, TimeUnit.SECONDS)
    }
}
