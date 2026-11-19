package io.micronaut.http.client.netty

import io.micronaut.http.client.exceptions.UnprocessedRequestException
import io.netty.channel.EventLoop
import spock.lang.Specification

import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/**
 * A closed client opens no connection: neither for a new request, nor for one its pool reserved
 * before it shut down.
 */
class ConnectionManagerShutdownSpec extends Specification {

    def 'connect fails once the client is closed'() {
        given:
        def server = new ServerSocket(0, 50, InetAddress.loopbackAddress)
        def uri = URI.create("http://127.0.0.1:${server.localPort}")
        def client = DefaultHttpClient.builder().uri(uri).build()
        def manager = client.connectionManager()
        def key = new DefaultHttpClient.RequestKey(client, uri)
        client.close()

        when:
        manager.connect(key, null, null).toCompletableFuture().get(10, TimeUnit.SECONDS)

        then:
        def e = thrown(ExecutionException)
        e.cause instanceof UnprocessedRequestException
        manager.pools.isEmpty()

        cleanup:
        server.close()
    }

    def 'a pool that shut down opens no connection'() {
        given: 'the pool of a client, which then closes'
        def server = new ServerSocket(0, 50, InetAddress.loopbackAddress)
        def uri = URI.create("http://127.0.0.1:${server.localPort}")
        def client = DefaultHttpClient.builder().uri(uri).build()
        def manager = client.connectionManager()
        manager.connect(new DefaultHttpClient.RequestKey(client, uri), null, null)
        def holder = manager.pools.values().first()
        EventLoop loop = manager.eventLoopGroup().next()
        client.close()

        when:
        holder.openNewConnection(loop)

        then:
        thrown(UnprocessedRequestException)

        cleanup:
        server.close()
    }
}
