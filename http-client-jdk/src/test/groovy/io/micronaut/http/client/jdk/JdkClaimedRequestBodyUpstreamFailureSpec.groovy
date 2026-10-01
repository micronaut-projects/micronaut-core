package io.micronaut.http.client.jdk

import io.micronaut.http.HttpRequest
import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.client.RawHttpClient
import io.micronaut.http.netty.body.NettyByteBodyFactory
import io.netty.buffer.ByteBuf
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.util.ResourceLeakDetector
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.nio.charset.StandardCharsets

/**
 * The body bytes of a raw exchange are released when the upstream cannot be reached, whatever
 * the exchange does with the error.
 */
class JdkClaimedRequestBodyUpstreamFailureSpec extends Specification {
    private static final int REPEAT = 20

    @Shared
    ResourceLeakDetector.Level previousLevel

    @Shared
    URI refused

    @Shared
    @AutoCleanup
    RawHttpClient rawClient

    void setupSpec() {
        previousLevel = ResourceLeakDetector.level
        ResourceLeakDetector.level = ResourceLeakDetector.Level.PARANOID
        int port
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.loopbackAddress)) {
            port = socket.localPort
        }
        refused = URI.create("http://127.0.0.1:$port/upstream")
        rawClient = RawHttpClient.create(refused)
    }

    void cleanupSpec() {
        ResourceLeakDetector.level = previousLevel
    }

    void "the client is the JDK client"() {
        expect:
        rawClient instanceof JdkRawHttpClient
    }

    void "the request body of a raw exchange is released when the upstream refuses the connection"() {
        when:
        List<ByteBuf> buffers = (1..REPEAT).collect {
            ByteBuf buf = buffer()
            CloseableByteBody body = new NettyByteBodyFactory(new EmbeddedChannel()).adapt(buf)
            Mono.from(rawClient.exchange(HttpRequest.POST(refused.toString(), null), body, null)).onErrorResume(e -> Mono.empty()).block()
            buf
        }

        then:
        buffers.every { it.refCnt() == 0 }
    }

    private static ByteBuf buffer() {
        return PooledByteBufAllocator.DEFAULT.directBuffer().writeBytes("claimed request body".getBytes(StandardCharsets.UTF_8))
    }
}
