package io.micronaut.http.client.netty

import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.HttpClientConfiguration
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.http.client.netty.ConnectionManager.PoolHolder.Http1ConnectionHolder
import io.micronaut.http.client.netty.ConnectionManager.PoolHolder.Http2ConnectionHolder
import io.netty.channel.MultiThreadIoEventLoopGroup
import io.netty.channel.nio.NioIoHandler
import io.netty.channel.socket.nio.NioSocketChannel
import spock.lang.Specification
import spock.lang.Timeout
import spock.lang.Unroll

import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

@Timeout(15)
class PoolWaiterLifecycleSpec extends Specification {
    @Unroll
    def '#version #queue waiter is unlinked on #termination'() {
        given:
        def fixture = new Fixture(version, queue)
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

        when:
        first.flow().cancel()
        fixture.barrier()

        then:
        fixture.queueSize() == 0

        when: 'cancellation also precedes initial dispatch'
        def cancelled = fixture.pool.createPendingRequest(null)
        cancelled.flow().cancel()
        cancelled.dispatch()
        fixture.barrier()

        then:
        fixture.queueSize() == 0

        cleanup:
        fixture.close()

        where:
        [version, queue, termination] << [
            ['V4_9', 'local', 'cancel'], ['V4_9', 'global', 'cancel'],
            ['V4_9', 'local', 'timeout'], ['V4_9', 'global', 'timeout']
        ]
    }

    def "V4_0 cancelled waiter stays queued and absorbs a connect failure"() {
        given:
        def fixture = new Fixture("V4_0", "global")
        Pool40 pool = (Pool40) fixture.pool
        def cancelled = pool.createPendingRequest(null)
        def waiting = pool.createPendingRequest(null)
        [cancelled, waiting].each { it.dispatch() }
        fixture.barrier()
        cancelled.flow().cancel()
        fixture.barrier()

        expect:
        fixture.queueSize() == 2

        when:
        pool.pendingConnectionCount.incrementAndGet()
        pool.onNewConnectionFailure(new IllegalStateException("connect failed"))
        fixture.barrier()

        then:
        fixture.queueSize() == 1
        !waiting.flow().toCompletableFuture().done

        cleanup:
        fixture.close()
    }

    @Unroll
    def '#version #queue pool close fails queued and subsequent waiters'() {
        given:
        def fixture = new Fixture(version, queue)
        def requests = (1..3).collect { fixture.pool.createPendingRequest(null) }
        requests.each { it.dispatch() }
        fixture.barrier()
        assert fixture.queueSize() == 3

        when:
        fixture.pool.close()
        fixture.barrier()

        then:
        fixture.queueSize() == 0
        requests.every { it.flow().tryCompleteError() instanceof HttpClientException }
        requests.every { it.flow().tryCompleteError().message.contains('closed') }

        when:
        def late = fixture.pool.createPendingRequest(null)
        late.dispatch()
        fixture.barrier()
        fixture.pool.close()

        then:
        late.flow().tryCompleteError() instanceof HttpClientException
        fixture.queueSize() == 0

        cleanup:
        fixture.close()

        where:
        version | queue
        'V4_9' | 'local'
        'V4_9' | 'global'
        'V4_0' | 'global'
    }

    @Unroll
    def '#version pool closes a late HTTP#protocol connection'() {
        given:
        def fixture = new Fixture(version, 'global')
        def channel = new NioSocketChannel()
        fixture.group.register(channel).sync()
        fixture.pool.close()
        fixture.barrier()

        when:
        channel.eventLoop().submit {
            if (protocol == 1) {
                def connection = new Http1ConnectionHolder(fixture.holder, channel, new NettyClientCustomizer() {})
                connection.poolEntry.onConnectionEstablished()
            } else {
                def connection = new Http2ConnectionHolder(fixture.holder, channel, new NettyClientCustomizer() {})
                connection.poolEntry.onConnectionEstablished(10)
            }
        }.sync()
        channel.closeFuture().sync()

        then:
        !channel.open
        fixture.queueSize() == 0

        cleanup:
        channel.close().sync()
        fixture.close()

        where:
        [version, protocol] << [['V4_9', 1], ['V4_9', 2], ['V4_0', 1], ['V4_0', 2]]
    }

    private static class Fixture implements AutoCloseable {
        final group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory())
        final DefaultHttpClient client
        final ConnectionManager.PoolHolder holder
        final Pool pool

        Fixture(String version, String queue) {
            def config = new DefaultHttpClientConfiguration()
            config.connectionPoolConfiguration.version = HttpClientConfiguration.ConnectionPoolConfiguration.PoolVersion.valueOf(version)
            config.connectionPoolConfiguration.maxPendingConnections = 0
            config.connectionPoolConfiguration.maxPendingAcquires = 3
            config.connectionPoolConfiguration.connectionLocality = queue == 'local' ?
                HttpClientConfiguration.ConnectionPoolConfiguration.ConnectionLocality.ENFORCED_ALWAYS :
                HttpClientConfiguration.ConnectionPoolConfiguration.ConnectionLocality.IGNORE
            client = DefaultHttpClient.builder().configuration(config).eventLoopGroup(group).build()
            holder = client.connectionManager().createPool(
                new NettyHttpClient.RequestKey(client.nettyHttpClient, URI.create('http://localhost')), group)
            pool = holder.pool
            if (pool instanceof Pool49) {
                pool.pickPreferredPoolOverride = queue == 'local' ? { it[0] } : { null }
            }
        }

        void barrier() {
            group.next().submit {}.sync()
        }

        int queueSize() {
            if (pool instanceof Pool40) {
                return pool.pendingRequests.size()
            }
            return pool.globalPendingRequests.size() + pool.localPools.sum { it.localPendingRequests.size() }
        }

        @Override
        void close() {
            pool.close()
            barrier()
            client.close()
            group.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync()
        }
    }
}
