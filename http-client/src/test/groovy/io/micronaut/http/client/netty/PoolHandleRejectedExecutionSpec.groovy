package io.micronaut.http.client.netty

import io.netty.channel.MultiThreadIoEventLoopGroup
import io.netty.channel.nio.NioIoHandler
import io.netty.util.ResourceLeakTracker
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@Timeout(15)
class PoolHandleRejectedExecutionSpec extends Specification {
    def 'a handle released after loop shutdown closes its tracker once (taint first: #taintFirst)'() {
        given:
        def group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory())
        def server = new ServerSocket(0, 50, InetAddress.loopbackAddress)
        def uri = URI.create("http://127.0.0.1:${server.localPort}")
        def client = DefaultHttpClient.builder().uri(uri).eventLoopGroup(group).build()
        def handle = client.connectionManager().connect(new NettyHttpClient.RequestKey(client.nettyHttpClient, uri), null)
                .toCompletableFuture().get(5, TimeUnit.SECONDS)
        server.soTimeout = 5000
        def socket = server.accept()
        def tracker = Mock(ResourceLeakTracker)
        // Make leak tracking deterministic regardless of Netty's sampling level.
        def trackerField = ConnectionManager.PoolHandle.getDeclaredField('tracker')
        trackerField.accessible = true
        def originalTracker = trackerField.get(handle)
        originalTracker?.close(handle)
        trackerField.set(handle, tracker)
        group.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync()

        when:
        if (taintFirst) {
            handle.taint()
        }
        def releases = (1..2).collect { CompletableFuture.runAsync { handle.release() } }
        releases.each { it.get(5, TimeUnit.SECONDS) }
        handle.release()

        then:
        noExceptionThrown()
        1 * tracker.close(handle)

        cleanup:
        socket?.close()
        client?.close()
        group?.shutdownGracefully(0, 0, TimeUnit.SECONDS)?.sync()
        server?.close()

        where:
        taintFirst << [false, true]
    }
}
