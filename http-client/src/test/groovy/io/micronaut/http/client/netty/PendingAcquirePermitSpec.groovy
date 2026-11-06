package io.micronaut.http.client.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.core.execution.DelayedExecutionFlow
import io.micronaut.http.HttpResponse
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelPromise
import io.netty.channel.EventLoop
import io.netty.channel.embedded.EmbeddedChannel
import reactor.core.Disposable
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

class PendingAcquirePermitSpec extends Specification {

    ApplicationContext ctx
    DefaultHttpClient client
    ConnectionManagerSpec.EmbeddedTestConnectionHttp1 conn

    def setup() {
        ctx = ApplicationContext.run([
                'micronaut.http.client.pool.max-pending-acquires'   : 1,
                'micronaut.http.client.pool.max-pending-connections': 1,
        ])
        client = ctx.getBean(DefaultHttpClient)
        conn = new ConnectionManagerSpec.EmbeddedTestConnectionHttp1()
        conn.setupHttp1()
        conn.openFuture = new CompletableFuture<>() // connection creation never completes
        patch(client, conn)
    }

    def cleanup() {
        client.close()
        ctx.close()
    }

    def 'rejected acquire does not release the permit of a pending acquire'() {
        when:
        def first = exchange()
        def second = exchange()
        def third = exchange()

        then:
        !first.isDone()
        second.completedExceptionally
        third.completedExceptionally
        pendingPermits() == 1
    }

    def 'cancelled pending acquire releases its permit once'() {
        when:
        Disposable first = Mono.from(client.exchange(conn.scheme + '://example.com/foo')).subscribe({}, {})
        conn.advance()
        first.dispose()

        then:
        pendingPermits() == 0

        when:
        def second = exchange()
        def third = exchange()

        then:
        !second.isDone()
        third.completedExceptionally
        pendingPermits() == 1
    }

    def 'acquire cancelled before admission does not hold a permit'() {
        given:
        def first = exchange()
        def holder = poolHolder()

        when: 'cancelled before dispatch, admission is rejected and holds nothing'
        def rejected = holder.pool.createPendingRequest(null)
        ((DelayedExecutionFlow) rejected.flow()).cancel()
        rejected.dispatch()

        then:
        !first.isDone()
        pendingPermits() == 1

        when: 'the pending acquire is cancelled, a request cancelled before dispatch is admitted without keeping a permit'
        ((DelayedExecutionFlow) firstRequest(holder).flow()).cancel()
        def admitted = holder.pool.createPendingRequest(null)
        ((DelayedExecutionFlow) admitted.flow()).cancel()
        admitted.dispatch()

        then:
        pendingPermits() == 0

        when:
        def second = exchange()
        def third = exchange()

        then:
        !second.isDone()
        third.completedExceptionally
        pendingPermits() == 1
    }

    def 'failed acquire releases its permit and the count never goes negative'() {
        when:
        def first = exchange()
        def holder = poolHolder()
        Pool49.PendingRequest request = firstRequest(holder)
        request.tryCompleteExceptionally(new RuntimeException('failed'))
        request.tryCompleteExceptionally(new RuntimeException('failed again'))
        ((DelayedExecutionFlow) request.flow()).cancel()

        then:
        first.completedExceptionally
        pendingPermits() == 0

        when:
        def second = exchange()
        def third = exchange()
        def fourth = exchange()

        then:
        !second.isDone()
        third.completedExceptionally
        fourth.completedExceptionally
        pendingPermits() == 1
    }

    private CompletableFuture<HttpResponse<?>> exchange() {
        def future = Mono.from(client.exchange(conn.scheme + '://example.com/foo')).toFuture()
        conn.advance()
        return future
    }

    private ConnectionManager.PoolHolder poolHolder() {
        def field = ConnectionManager.getDeclaredField('pools')
        field.accessible = true
        Map<?, ConnectionManager.PoolHolder> pools = field.get(client.connectionManager)
        assert pools.size() == 1
        return pools.values().first()
    }

    private int pendingPermits() {
        Pool49 pool = (Pool49) poolHolder().pool
        return pool.globalPending.intValue()
    }

    private static Pool49.PendingRequest firstRequest(ConnectionManager.PoolHolder poolHolder) {
        Pool49 pool = (Pool49) poolHolder.pool
        for (def pair : pool.localPools) {
            def request = pair.localPendingRequests.peek()
            if (request != null) {
                return request
            }
        }
        return pool.globalPendingRequests.peek()
    }

    private static void patch(DefaultHttpClient httpClient, ConnectionManagerSpec.EmbeddedTestConnectionBase connection) {
        connection.clientChannel = new EmbeddedChannel(new DummyChannelId('client'), connection.clientInitializer) {
            def loop

            @Override
            EventLoop eventLoop() {
                if (loop == null) {
                    loop = new DelegateEventLoop(super.eventLoop()) {
                        @Override
                        boolean inEventLoop() {
                            return connection.inEventLoop
                        }
                    }
                }
                return loop
            }
        }
        ChannelPromise openFuture = connection.clientChannel.newPromise()
        connection.openFuture.whenComplete((v, t) -> {
            if (t == null) openFuture.setSuccess()
            else openFuture.setFailure(t)
        })
        httpClient.connectionManager = new EmbeddedConnectionManager(httpClient.connectionManager, [connection.clientChannel], [(ChannelFuture) openFuture])
    }
}
