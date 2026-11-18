package io.micronaut.http.client.netty

import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.HttpClientConfiguration
import io.netty.channel.MultiThreadIoEventLoopGroup
import io.netty.channel.nio.NioIoHandler
import spock.lang.Specification
import spock.lang.Timeout
import spock.lang.Unroll

import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/**
 * A request that waits for a connection leaves the queue of the pool when it is cancelled or its
 * acquire times out, without disturbing the requests queued around it.
 */
@Timeout(15)
class PoolWaiterLifecycleSpec extends Specification {
    @Unroll
    def '#queue waiter is unlinked on #termination'() {
        given:
        def fixture = new Fixture(queue)
        def first = fixture.pool.createPendingRequest(null)
        def request = fixture.pool.createPendingRequest(null)
        def last = fixture.pool.createPendingRequest(null)
        [first, request, last].each { it.dispatch() }
        fixture.barrier()
        assert fixture.queueSize() == 3

        when:
        if (termination == 'cancel') {
            request.flow().cancel()
        } else {
            def timed = request.flow().timeout(Duration.ofMillis(10), fixture.group, null)
            try {
                timed.toCompletableFuture().get(5, TimeUnit.SECONDS)
                assert false: 'acquire should time out'
            } catch (ExecutionException e) {
                assert e.cause instanceof java.util.concurrent.TimeoutException
            }
        }
        fixture.barrier()

        then:
        fixture.queueSize() == 2
        !request.tryCompleteExceptionally(new IllegalStateException('late failure'))

        when: 'the neighbours are still linked and can also be removed'
        last.flow().cancel()
        fixture.barrier()

        then:
        fixture.queueSize() == 1

        when: 'cancellation also precedes initial dispatch'
        def cancelled = fixture.pool.createPendingRequest(null)
        cancelled.flow().cancel()
        cancelled.dispatch()
        fixture.barrier()

        then:
        fixture.queueSize() == 1

        when:
        first.flow().cancel()
        fixture.barrier()

        then: 'nothing waits, so the pool is no longer used'
        fixture.queueSize() == 0
        fixture.pool.retired

        cleanup:
        fixture.close()

        where:
        [queue, termination] << [['local', 'global'], ['cancel', 'timeout']].combinations()
    }

    private static class Fixture implements AutoCloseable {
        final group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory())
        final DefaultHttpClient client
        final Pool49 pool

        Fixture(String queue) {
            def config = new DefaultHttpClientConfiguration()
            // no connection can be opened, so every request stays queued
            config.connectionPoolConfiguration.maxPendingConnections = 0
            config.connectionPoolConfiguration.maxPendingAcquires = 3
            config.connectionPoolConfiguration.connectionLocality = queue == 'local' ?
                HttpClientConfiguration.ConnectionPoolConfiguration.ConnectionLocality.ENFORCED_ALWAYS :
                HttpClientConfiguration.ConnectionPoolConfiguration.ConnectionLocality.IGNORE
            client = DefaultHttpClient.builder().configuration(config).eventLoopGroup(group).build()
            pool = (Pool49) client.connectionManager().createPool(
                new NettyHttpClient.RequestKey(client.nettyHttpClient, URI.create('http://localhost')), group).pool
            pool.pickPreferredPoolOverride = queue == 'local' ? { it[0] } : { null }
        }

        void barrier() {
            group.next().submit {}.sync()
        }

        int queueSize() {
            return pool.globalPendingRequests.size() + pool.localPools.sum { it.localPendingRequests.size() }
        }

        @Override
        void close() {
            client.close()
            group.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync()
        }
    }
}
