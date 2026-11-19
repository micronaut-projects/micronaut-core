package io.micronaut.http.client.netty

import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.HttpClientConfiguration
import io.netty.channel.DefaultEventLoopGroup
import io.netty.channel.EventLoop
import spock.lang.Specification

import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier

/**
 * A shut down {@link Pool49} fails the requests that wait for a connection, and those that reach
 * it later, and opens no connection for them.
 */
class Pool49ShutdownSpec extends Specification {

    private static final Supplier<Throwable> ERROR = { new IllegalStateException('closed') }

    def group = new DefaultEventLoopGroup(1)
    EventLoop loop = group.next()

    List<DefaultHttpClient> clients = []

    def cleanup() {
        clients.each { it.close() }
        group.shutdownGracefully(0, 0, TimeUnit.SECONDS)
    }

    def 'a request dispatched after shutdown fails'() {
        given: 'a request that may only wait on its own loop, where the pool drained the queue already'
        def pool = pool(config(1), true)
        pool.shutdown(ERROR)

        when:
        def request = pool.createPendingRequest(null)
        CompletableFuture<?> acquired = request.flow().toCompletableFuture()
        request.dispatch()

        then:
        failedWithError(acquired)
    }

    def 'shutdown opens no connection'() {
        given:
        def pool = pool(config(1), false)
        pool.shutdown(ERROR)

        expect:
        !onLoop { pool.openConnectionStep1() }
    }

    def 'a request queued globally while the pool shuts down fails'() {
        given: 'the pool shuts down while the request checks the connection limit'
        AtomicReference<Runnable> duringLimitCheck = new AtomicReference<>()
        def config = new HttpClientConfiguration.ConnectionPoolConfiguration() {
            @Override
            int getMaxPendingConnections() {
                duringLimitCheck.getAndSet(null)?.run()
                return 0
            }
        }
        def pool = pool(config, false)
        duringLimitCheck.set { pool.shutdown(ERROR) }

        when:
        def request = pool.createPendingRequest(null)
        CompletableFuture<?> acquired = request.flow().toCompletableFuture()
        onLoop { request.dispatch() }

        then:
        duringLimitCheck.get() == null
        failedWithError(acquired)
        pool.globalPendingRequests.isEmpty()
    }

    def 'shutdown fails the local requests of a terminated event loop'() {
        given: 'a request that waits in the local queue of a loop that then terminates'
        def pool = pool(config(0), true)
        def request = pool.createPendingRequest(null)
        CompletableFuture<?> acquired = request.flow().toCompletableFuture()
        onLoop { request.dispatch() }
        assert onLoop { pool.localPools[0].localPendingRequests.size() } == 1
        group.shutdownGracefully(0, 0, TimeUnit.SECONDS)
        assert group.awaitTermination(10, TimeUnit.SECONDS)

        when:
        pool.shutdown(ERROR)

        then:
        failedWithError(acquired)
    }

    def 'a connection established after shutdown fails the local request it would serve'() {
        given: 'a request that waits in the local queue'
        def limit = new AtomicInteger(0)
        def config = new HttpClientConfiguration.ConnectionPoolConfiguration() {
            @Override
            int getMaxPendingConnections() {
                return limit.get()
            }
        }
        def pool = pool(config, true)
        def request = pool.createPendingRequest(null)
        CompletableFuture<?> acquired = request.flow().toCompletableFuture()
        onLoop { request.dispatch() }

        and: 'a connection that is opening for it'
        onLoop {
            limit.set(1)
            assert pool.openConnectionStep1()
            pool.localPools[0].openConnectionStep2()
        }

        when: 'the connection is established on the loop after shutdown, before the loop drains its queue'
        def blocker = new CountDownLatch(1)
        loop.execute { blocker.await() }
        def established = loop.submit({
            pool.createHttp1PoolEntry(loop, null).onConnectionEstablished()
        } as Runnable)
        pool.shutdown(ERROR)
        blocker.countDown()
        established.get(10, TimeUnit.SECONDS)

        then:
        failedWithError(acquired)
    }

    private Pool49 pool(HttpClientConfiguration.ConnectionPoolConfiguration config, boolean local) {
        if (local) {
            config.connectionLocality = HttpClientConfiguration.ConnectionPoolConfiguration.ConnectionLocality.ENFORCED_ALWAYS
        }
        def configuration = new DefaultHttpClientConfiguration() {
            @Override
            HttpClientConfiguration.ConnectionPoolConfiguration getConnectionPoolConfiguration() {
                return config
            }
        }
        def uri = URI.create('http://127.0.0.1:1')
        def client = DefaultHttpClient.builder().uri(uri).configuration(configuration).build()
        clients.add(client)
        // a real listener, which opens no connection here: the tests open none, or only reserve one
        def pool = (Pool49) client.connectionManager().createPool(new DefaultHttpClient.RequestKey(client, uri), group).pool
        pool.pickPreferredPoolOverride = { pools -> local ? pools[0] : null }
        return pool
    }

    private static HttpClientConfiguration.ConnectionPoolConfiguration config(int maxPendingConnections) {
        def config = new HttpClientConfiguration.ConnectionPoolConfiguration()
        config.maxPendingConnections = maxPendingConnections
        return config
    }

    private static boolean failedWithError(CompletableFuture<?> future) {
        try {
            future.get(10, TimeUnit.SECONDS)
            return false
        } catch (ExecutionException e) {
            return e.cause instanceof IllegalStateException && e.cause.message == 'closed'
        }
    }

    private <T> T onLoop(Closure<T> task) {
        return loop.submit(task as Callable<T>).get(10, TimeUnit.SECONDS)
    }
}
