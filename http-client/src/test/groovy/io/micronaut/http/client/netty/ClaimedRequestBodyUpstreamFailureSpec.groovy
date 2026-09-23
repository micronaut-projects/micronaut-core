package io.micronaut.http.client.netty

import io.micronaut.core.convert.ConversionService
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.MutableHttpRequestWrapper
import io.micronaut.http.ServerHttpRequest
import io.micronaut.http.body.ByteBody
import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.client.AsyncRawHttpClient
import io.micronaut.http.client.ProxyHttpClient
import io.micronaut.http.client.ProxyRequestOptions
import io.micronaut.http.client.RawHttpClient
import io.micronaut.http.client.RawHttpClientSupport
import io.micronaut.http.client.RawRequestOptions
import io.micronaut.http.netty.body.NettyByteBodyFactory
import io.netty.buffer.ByteBuf
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.util.ResourceLeakDetector
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

/**
 * The body bytes a proxy or raw exchange claims from a server request are released when the
 * upstream cannot be reached, whatever the exchange does with the error.
 */
class ClaimedRequestBodyUpstreamFailureSpec extends Specification {
    private static final int REPEAT = 20

    @Shared
    ResourceLeakDetector.Level previousLevel

    @Shared
    URI refused

    @Shared
    @AutoCleanup
    ProxyHttpClient proxyClient

    @Shared
    @AutoCleanup
    RawHttpClient rawClient

    @Shared
    @AutoCleanup
    AsyncRawHttpClient asyncRawClient

    void setupSpec() {
        previousLevel = ResourceLeakDetector.level
        ResourceLeakDetector.level = ResourceLeakDetector.Level.PARANOID
        int port
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.loopbackAddress)) {
            port = socket.localPort
        }
        refused = URI.create("http://127.0.0.1:$port/upstream")
        proxyClient = ProxyHttpClient.create(refused.toURL())
        rawClient = RawHttpClient.create(refused)
        asyncRawClient = AsyncRawHttpClient.create(refused)
    }

    void cleanupSpec() {
        ResourceLeakDetector.level = previousLevel
    }

    void "a proxied server request body is released when the upstream refuses the connection"() {
        when:
        List<ByteBuf> buffers = (1..REPEAT).collect {
            ByteBuf buf = buffer()
            Mono.from(proxyClient.proxy(serverRequest(buf))).onErrorResume(e -> Mono.empty()).block()
            buf
        }

        then:
        buffers.every { it.refCnt() == 0 }
    }

    void "a proxied server request body is released when the upstream refuses the connection, with proxy options"() {
        when:
        List<ByteBuf> buffers = (1..REPEAT).collect {
            ByteBuf buf = buffer()
            Mono.from(proxyClient.proxy(serverRequest(buf), ProxyRequestOptions.builder().retainHostHeader(true).build())).onErrorResume(e -> Mono.empty()).block()
            buf
        }

        then:
        buffers.every { it.refCnt() == 0 }
    }

    void "a proxied server request body is released when the proxy exchange is cancelled"() {
        when:
        List<ByteBuf> buffers = (1..REPEAT).collect {
            ByteBuf buf = buffer()
            Flux.from(proxyClient.proxy(serverRequest(buf))).subscribe({}, {}).dispose()
            buf
        }

        then:
        new PollingConditions(timeout: 10).eventually {
            assert buffers.every { it.refCnt() == 0 }
        }
    }

    void "a claimed server request body of a raw exchange is released when the upstream refuses the connection"() {
        when:
        List<ByteBuf> buffers = (1..REPEAT).collect {
            ByteBuf buf = buffer()
            def request = serverRequest(buf)
            CloseableByteBody claimed = RawHttpClientSupport.claimServerRequestBody(request)
            def exchange = options == null
                ? rawClient.exchange(request, claimed, null)
                : rawClient.exchange(request, claimed, null, options)
            Mono.from(exchange).onErrorResume(e -> Mono.empty()).block()
            buf
        }

        then:
        buffers.every { it.refCnt() == 0 }

        where:
        options << [null, RawRequestOptions.getDefault(), RawRequestOptions.proxy()]
    }

    void "a claimed server request body of an async raw exchange is released when the upstream refuses the connection"() {
        when:
        List<ByteBuf> buffers = (1..REPEAT).collect {
            ByteBuf buf = buffer()
            def request = serverRequest(buf)
            CloseableByteBody claimed = RawHttpClientSupport.claimServerRequestBody(request)
            CompletableFuture<?> future = (options == null
                ? asyncRawClient.exchange(request, claimed)
                : asyncRawClient.exchange(request, claimed, options)).toCompletableFuture()
            try {
                future.get()
            } catch (ExecutionException ignored) {
            }
            buf
        }

        then:
        buffers.every { it.refCnt() == 0 }

        where:
        options << [null, RawRequestOptions.getDefault(), RawRequestOptions.proxy()]
    }

    private static ByteBuf buffer() {
        return PooledByteBufAllocator.DEFAULT.directBuffer().writeBytes("claimed request body".getBytes(StandardCharsets.UTF_8))
    }

    private ServerHttpRequest<Object> serverRequest(ByteBuf buf) {
        CloseableByteBody body = new NettyByteBodyFactory(new EmbeddedChannel()).adapt(buf)
        return new TestServerRequest(HttpRequest.POST(refused.toString(), null), body)
    }

    /**
     * A server request whose body bytes the clients claim, like a Netty server request.
     */
    static class TestServerRequest extends MutableHttpRequestWrapper<Object> implements ServerHttpRequest<Object> {
        private final CloseableByteBody body

        TestServerRequest(MutableHttpRequest<Object> delegate, CloseableByteBody body) {
            super(ConversionService.SHARED, delegate)
            this.body = body
        }

        @Override
        ByteBody byteBody() {
            return body
        }
    }
}
