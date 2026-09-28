package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http2.DefaultHttp2DataFrame
import io.netty.handler.codec.http2.DefaultHttp2Headers
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame
import io.netty.handler.codec.http2.DefaultHttp2ResetFrame
import io.netty.handler.codec.http2.Http2ChannelDuplexHandler
import io.netty.handler.codec.http2.Http2Error
import io.netty.handler.codec.http2.Http2FrameCodecBuilder
import spock.lang.Specification

import java.nio.charset.StandardCharsets

/**
 * An HTTP/2 client that sends request data and then resets the stream or disconnects must not
 * leave the data unreleased. Leaks are reported by the buffer leak detection of this test suite.
 */
class Http2ResetRequestLeakSpec extends Specification {

    void "request data is released: #route, reset #reset"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                      : 'Http2ResetRequestLeakSpec',
                'micronaut.server.http-version'  : '2.0',
        ])
        def duplexHandler = new Http2ChannelDuplexHandler() {}
        def client = new EmbeddedChannel(Http2FrameCodecBuilder.forClient().build(), duplexHandler)
        def stream = duplexHandler.newStream()
        def headers = new DefaultHttp2Headers()
        headers.method(HttpMethod.POST.asciiName())
        headers.scheme("http")
        headers.authority("localhost")
        headers.path(route)
        headers.set("content-type", MediaType.TEXT_PLAIN)
        client.writeOutbound(new DefaultHttp2HeadersFrame(headers, false).stream(stream))
        client.writeOutbound(new DefaultHttp2DataFrame(Unpooled.copiedBuffer("foo", StandardCharsets.UTF_8), false).stream(stream))
        if (reset) {
            client.writeOutbound(new DefaultHttp2ResetFrame(Http2Error.CANCEL).stream(stream))
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream()
        ByteBuf msg
        while ((msg = client.readOutbound()) != null) {
            msg.readBytes(bytes, msg.readableBytes())
            msg.release()
        }
        Socket socket = new Socket(server.host, server.port)

        when:
        socket.outputStream.write(bytes.toByteArray())
        socket.outputStream.flush()
        Thread.sleep(1000)
        socket.close()
        Thread.sleep(1000)
        // stop the server, so that anything it still holds can be collected and reported by this test
        server.stop()

        then:
        noExceptionThrown()

        cleanup:
        client.finishAndReleaseAll()
        server.stop()

        where:
        route              | reset
        '/h2reset/string'  | true
        '/h2reset/string'  | false
    }

    @Requires(property = 'spec.name', value = 'Http2ResetRequestLeakSpec')
    @Controller('/h2reset')
    static class ResetController {
        @Post(value = '/string', consumes = MediaType.TEXT_PLAIN)
        String string(@Body String body) {
            return body
        }
    }
}
