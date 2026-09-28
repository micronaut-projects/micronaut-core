package io.micronaut.http.server.netty.binders

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Part
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.exceptions.HttpStatusException
import io.micronaut.http.multipart.PartData
import io.micronaut.http.multipart.RawFormField
import io.micronaut.http.multipart.StreamingFileUpload
import io.micronaut.http.server.multipart.MultipartBody
import io.micronaut.http.server.netty.EmbeddedTestUtil
import io.micronaut.http.server.netty.NettyHttpServer
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultHttpContent
import io.netty.handler.codec.http.DefaultHttpRequest
import io.netty.handler.codec.http.DefaultLastHttpContent
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import jakarta.validation.constraints.Min
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.charset.StandardCharsets

/**
 * A streaming body argument claims the request body when it is bound. If the request is rejected
 * before the route consumes the argument, the body must be discarded, or the connection stalls.
 */
class UnconsumedStreamingBodySpec extends Specification {
    private static final String BOUNDARY = "----boundary"

    @Unroll
    def 'rejected #path does not stall the connection'() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                'spec.name': 'UnconsumedStreamingBodySpec',
        ])
        def embeddedServer = (NettyHttpServer) ctx.getBean(EmbeddedServer)

        def serverEmbeddedChannel = embeddedServer.buildEmbeddedChannel(false)
        def clientEmbeddedChannel = new EmbeddedChannel()
        clientEmbeddedChannel.config().setAutoRead(true)

        EmbeddedTestUtil.connect(serverEmbeddedChannel, clientEmbeddedChannel)

        clientEmbeddedChannel.pipeline()
                .addLast(new HttpClientCodec())
                .addLast(new HttpObjectAggregator(1024))

        String first
        String rest
        String contentType
        if (multipart) {
            contentType = "multipart/form-data; boundary=" + BOUNDARY
            first = "--" + BOUNDARY + "\r\n" +
                    "Content-Disposition: form-data; name=\"file\"; filename=\"a.txt\"\r\n" +
                    "Content-Type: text/plain\r\n" +
                    "\r\n" +
                    "x" * 1000
            rest = "y" * 100000 + "\r\n--" + BOUNDARY + "--\r\n"
        } else {
            contentType = MediaType.APPLICATION_OCTET_STREAM
            first = "x" * 1000
            rest = "y" * 100000
        }

        def request1 = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, path)
        request1.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType)
        request1.headers().set(HttpHeaderNames.CONTENT_LENGTH, first.length() + rest.length())

        when:
        clientEmbeddedChannel.writeOneOutbound(request1)
        clientEmbeddedChannel.writeOneOutbound(new DefaultHttpContent(Unpooled.copiedBuffer(first, StandardCharsets.UTF_8)))
        clientEmbeddedChannel.flushOutbound()
        EmbeddedTestUtil.advance(serverEmbeddedChannel, clientEmbeddedChannel)

        then:
        FullHttpResponse response1 = clientEmbeddedChannel.readInbound()
        response1 != null
        response1.status() == HttpResponseStatus.BAD_REQUEST

        when:
        // separate packets, so that the server only reads them while it has read credit left
        for (int i = 0; i < rest.length(); i += 1000) {
            clientEmbeddedChannel.writeOneOutbound(new DefaultHttpContent(Unpooled.copiedBuffer(rest.substring(i, Math.min(rest.length(), i + 1000)), StandardCharsets.UTF_8)))
            clientEmbeddedChannel.flushOutbound()
            EmbeddedTestUtil.advance(serverEmbeddedChannel, clientEmbeddedChannel)
        }
        clientEmbeddedChannel.writeOneOutbound(new DefaultLastHttpContent())
        clientEmbeddedChannel.flushOutbound()
        EmbeddedTestUtil.advance(serverEmbeddedChannel, clientEmbeddedChannel)
        clientEmbeddedChannel.writeOneOutbound(new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/unconsumed/ok"))
        clientEmbeddedChannel.flushOutbound()
        EmbeddedTestUtil.advance(serverEmbeddedChannel, clientEmbeddedChannel)

        then:
        FullHttpResponse response2 = clientEmbeddedChannel.readInbound()
        response2 != null
        response2.status() == HttpResponseStatus.OK

        cleanup:
        response1?.release()
        response2?.release()
        clientEmbeddedChannel.close()
        serverEmbeddedChannel.close()
        ctx.close()

        where:
        path                                               | multipart
        '/unconsumed/publisher-unsatisfied'                | false
        '/unconsumed/publisher-throws'                     | false
        '/unconsumed/publisher-invalid?value=1'            | false
        '/unconsumed/input-stream-unsatisfied'             | false
        '/unconsumed/input-stream-throws'                  | false
        '/unconsumed/input-stream-invalid?value=1'         | false
        '/unconsumed/streaming-upload-throws'              | true
        '/unconsumed/streaming-upload-invalid?value=1'     | true
        '/unconsumed/streaming-upload-unsatisfied'         | true
        '/unconsumed/raw-field-throws'                     | true
        '/unconsumed/raw-field-invalid?value=1'            | true
        '/unconsumed/part-publisher-throws'                | true
        '/unconsumed/part-publisher-invalid?value=1'       | true
        '/unconsumed/part-publisher-unsatisfied'           | true
        '/unconsumed/multipart-body-throws'                | true
        '/unconsumed/multipart-body-unsatisfied'           | true
    }

    def 'route that consumes the publisher after returning still gets the full body'() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                'spec.name': 'UnconsumedStreamingBodySpec',
        ])
        def embeddedServer = (NettyHttpServer) ctx.getBean(EmbeddedServer)

        def serverEmbeddedChannel = embeddedServer.buildEmbeddedChannel(false)
        def clientEmbeddedChannel = new EmbeddedChannel()
        clientEmbeddedChannel.config().setAutoRead(true)

        EmbeddedTestUtil.connect(serverEmbeddedChannel, clientEmbeddedChannel)

        clientEmbeddedChannel.pipeline()
                .addLast(new HttpClientCodec())
                .addLast(new HttpObjectAggregator(1024))

        int length = 101000
        def request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/unconsumed/publisher-later")
        request.headers().set(HttpHeaderNames.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM)
        request.headers().set(HttpHeaderNames.CONTENT_LENGTH, length)

        when:
        clientEmbeddedChannel.writeOneOutbound(request)
        clientEmbeddedChannel.flushOutbound()
        EmbeddedTestUtil.advance(serverEmbeddedChannel, clientEmbeddedChannel)
        for (int i = 0; i < length; i += 1000) {
            clientEmbeddedChannel.writeOneOutbound(new DefaultHttpContent(Unpooled.copiedBuffer("x" * Math.min(1000, length - i), StandardCharsets.UTF_8)))
            clientEmbeddedChannel.flushOutbound()
            EmbeddedTestUtil.advance(serverEmbeddedChannel, clientEmbeddedChannel)
        }
        clientEmbeddedChannel.writeOneOutbound(new DefaultLastHttpContent())
        clientEmbeddedChannel.flushOutbound()
        EmbeddedTestUtil.advance(serverEmbeddedChannel, clientEmbeddedChannel)

        then:
        FullHttpResponse response = clientEmbeddedChannel.readInbound()
        response != null
        response.status() == HttpResponseStatus.OK
        response.content().toString(StandardCharsets.UTF_8) == String.valueOf(length)

        cleanup:
        response?.release()
        clientEmbeddedChannel.close()
        serverEmbeddedChannel.close()
        ctx.close()
    }

    @Controller("/unconsumed")
    @Requires(property = "spec.name", value = "UnconsumedStreamingBodySpec")
    static class MyController {
        @Post(uri = "/publisher-unsatisfied", consumes = MediaType.APPLICATION_OCTET_STREAM)
        String publisherUnsatisfied(@Body Publisher<byte[]> body, @QueryValue String missing) {
            return "unreachable"
        }

        @Post(uri = "/publisher-throws", consumes = MediaType.APPLICATION_OCTET_STREAM)
        String publisherThrows(@Body Publisher<byte[]> body) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "rejected")
        }

        @Post(uri = "/publisher-invalid", consumes = MediaType.APPLICATION_OCTET_STREAM)
        String publisherInvalid(@Body Publisher<byte[]> body, @QueryValue @Min(10) int value) {
            return "unreachable"
        }

        @Post(uri = "/input-stream-unsatisfied", consumes = MediaType.APPLICATION_OCTET_STREAM)
        String inputStreamUnsatisfied(@Body InputStream body, @QueryValue String missing) {
            return "unreachable"
        }

        @Post(uri = "/input-stream-throws", consumes = MediaType.APPLICATION_OCTET_STREAM)
        String inputStreamThrows(@Body InputStream body) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "rejected")
        }

        @Post(uri = "/input-stream-invalid", consumes = MediaType.APPLICATION_OCTET_STREAM)
        String inputStreamInvalid(@Body InputStream body, @QueryValue @Min(10) int value) {
            return "unreachable"
        }

        @Post(uri = "/streaming-upload-throws", consumes = MediaType.MULTIPART_FORM_DATA)
        String streamingUploadThrows(StreamingFileUpload file) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "rejected")
        }

        @Post(uri = "/streaming-upload-invalid", consumes = MediaType.MULTIPART_FORM_DATA)
        String streamingUploadInvalid(StreamingFileUpload file, @QueryValue @Min(10) int value) {
            return "unreachable"
        }

        @Post(uri = "/streaming-upload-unsatisfied", consumes = MediaType.MULTIPART_FORM_DATA)
        String streamingUploadUnsatisfied(StreamingFileUpload file, @QueryValue String missing) {
            return "unreachable"
        }

        @Post(uri = "/raw-field-throws", consumes = MediaType.MULTIPART_FORM_DATA)
        String rawFieldThrows(RawFormField file) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "rejected")
        }

        @Post(uri = "/raw-field-invalid", consumes = MediaType.MULTIPART_FORM_DATA)
        String rawFieldInvalid(RawFormField file, @QueryValue @Min(10) int value) {
            return "unreachable"
        }

        @Post(uri = "/part-publisher-unsatisfied", consumes = MediaType.MULTIPART_FORM_DATA)
        String partPublisherUnsatisfied(@Part("file") Publisher<PartData> file, @QueryValue String missing) {
            return "unreachable"
        }

        @Post(uri = "/publisher-later", consumes = MediaType.APPLICATION_OCTET_STREAM, produces = MediaType.TEXT_PLAIN)
        Mono<String> publisherLater(@Body Publisher<byte[]> body) {
            // the body is only subscribed to after the route returned
            return Flux.from(body).map(b -> (long) b.length).reduce(0L, Long::sum).map(String::valueOf)
        }

        @Post(uri = "/part-publisher-throws", consumes = MediaType.MULTIPART_FORM_DATA)
        String partPublisherThrows(@Part("file") Publisher<PartData> file) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "rejected")
        }

        @Post(uri = "/part-publisher-invalid", consumes = MediaType.MULTIPART_FORM_DATA)
        String partPublisherInvalid(@Part("file") Publisher<PartData> file, @QueryValue @Min(10) int value) {
            return "unreachable"
        }

        @Post(uri = "/multipart-body-throws", consumes = MediaType.MULTIPART_FORM_DATA)
        String multipartBodyThrows(@Body MultipartBody body) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "rejected")
        }

        @Post(uri = "/multipart-body-unsatisfied", consumes = MediaType.MULTIPART_FORM_DATA)
        String multipartBodyUnsatisfied(@Body MultipartBody body, @QueryValue String missing) {
            return "unreachable"
        }

        @Get("/ok")
        String ok() {
            return "ok"
        }
    }
}
