package io.micronaut.http.client.netty

import io.micronaut.core.io.buffer.ByteBufferFactory
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.core.propagation.PropagatedContextElement
import io.micronaut.core.propagation.PropagatedContextConfiguration
import io.micronaut.core.type.Argument
import io.micronaut.core.type.MutableHeaders
import io.micronaut.core.util.ExceptionUtils
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.body.MessageBodyHandlerRegistry
import io.micronaut.http.body.MessageBodyWriter
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.multipart.HttpPostRequestEncoder
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier

@Timeout(15)
class RawRequestPreparationSpec extends Specification {
    def 'the off-loop request writer sees the caller context and restores the loop context (#mode)'() {
        given:
        def previousMode = PropagatedContextConfiguration.get()
        PropagatedContextConfiguration.set(mode)
        def server = new ServerSocket(0, 50, InetAddress.loopbackAddress)
        def uri = URI.create("http://127.0.0.1:${server.localPort}")
        def client = DefaultHttpClient.builder().uri(uri).build()
        def handle = client.connectionManager().connect(new NettyHttpClient.RequestKey(client.nettyHttpClient, uri), null)
                .toCompletableFuture().get(5, TimeUnit.SECONDS)
        server.soTimeout = 5000
        def socket = server.accept()
        def seen = new CompletableFuture<PropagatedContext>()
        def writer = Stub(MessageBodyWriter) {
            writeTo(_, _, _, _, _ as ByteBufferFactory) >> { args ->
                seen.complete(PropagatedContext.getOrEmpty())
                args[4].buffer()
            }
        }
        client.setHandlerRegistry(Stub(MessageBodyHandlerRegistry) {
            getWriter(_, _) >> writer
        })
        def context = PropagatedContext.empty().plus(new User('caller'))

        when:
        def response = context.propagate({ ->
            client.nettyHttpClient.sendRawRequest(handle, HttpRequest.POST(uri, new Object()), new AtomicReference<ScheduledExecutorService>())
        } as Supplier)
        def captured = seen.get(5, TimeUnit.SECONDS)
        socket.outputStream.write('HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n'.bytes)
        socket.outputStream.flush()
        def result = response.toCompletableFuture().get(5, TimeUnit.SECONDS)
        result.close()

        then:
        captured.find(User).orElseThrow().name == 'caller'
        handle.channel().eventLoop().submit { PropagatedContext.getOrEmpty().find(User).isEmpty() }.get(5, TimeUnit.SECONDS)

        cleanup:
        socket?.close()
        client?.close()
        server?.close()
        PropagatedContextConfiguration.set(previousMode)

        where:
        mode << PropagatedContextConfiguration.Mode.values()
    }

    def 'a pre-write failure releases the connection and only encoder failures keep it reusable (#failure.class.simpleName)'() {
        given:
        def client = DefaultHttpClient.builder().build()
        def channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter())
        def holder = client.connectionManager().createPool(
                new NettyHttpClient.RequestKey(client.nettyHttpClient, URI.create('http://localhost')), [channel.eventLoop()])
        def connection = new ConnectionManager.PoolHolder.Http1ConnectionHolder(holder, channel, new NettyClientCustomizer() {})
        connection.release()
        def pending = holder.pool.createPendingRequest(null)
        connection.dispatch0(pending)
        def handle = pending.flow().toCompletableFuture().join()
        def writer = new FailingWriter(failure)
        client.setHandlerRegistry(Stub(MessageBodyHandlerRegistry) {
            getWriter(_, _) >> writer
        })

        when:
        def response = client.nettyHttpClient.sendRawRequest(handle, HttpRequest.POST('http://localhost/', new Object()), new AtomicReference<ScheduledExecutorService>())

        then:
        response.tryCompleteError().is(failure)
        channel.readOutbound() == null
        connection.liveRequestCount() == 0
        connection.state == (reusable ? ConnectionManager.PoolHolder.ConnectionState.IDLE : ConnectionManager.PoolHolder.ConnectionState.CLOSED)
        channel.active == reusable
        handle.canReturn() == reusable

        cleanup:
        channel?.finishAndReleaseAll()
        holder?.pool?.close()
        client?.close()

        where:
        failure                                                                                | reusable
        new HttpPostRequestEncoder.ErrorDataEncoderException('multipart encoding failed')       | true
        new IllegalStateException('writer failed')                                              | false
        new AssertionError('writer failed')                                                     | false
    }

    private static class FailingWriter implements MessageBodyWriter<Object> {
        private final Throwable failure

        FailingWriter(Throwable failure) {
            this.failure = failure
        }

        @Override
        void writeTo(Argument<Object> type, MediaType mediaType, Object value, MutableHeaders headers, OutputStream output) {
            ExceptionUtils.sneakyThrow(failure)
        }
    }

    private static class User implements PropagatedContextElement {
        final String name

        User(String name) {
            this.name = name
        }
    }
}
