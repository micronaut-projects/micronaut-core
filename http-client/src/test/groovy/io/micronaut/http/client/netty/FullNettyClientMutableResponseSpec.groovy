package io.micronaut.http.client.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.convert.ConversionService
import io.micronaut.http.ByteBodyHttpResponse
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.body.MessageBodyHandlerRegistry
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.buffer.ByteBuf
import io.netty.buffer.PooledByteBufAllocator
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.nio.charset.StandardCharsets

/**
 * The mutable copy of a full client response keeps the received body bytes.
 */
class FullNettyClientMutableResponseSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'FullNettyClientMutableResponseSpec'])

    @Shared
    @AutoCleanup
    HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    void "the mutable copy of an error response keeps the error body"() {
        when:
        client.toBlocking().exchange(HttpRequest.GET('/mutable-response/missing'), Map)

        then:
        HttpClientResponseException e = thrown()
        MutableHttpResponse<?> mutable = e.response.toMutableResponse()
        mutable instanceof ByteBodyHttpResponse
        mutable.status == HttpStatus.NOT_FOUND
        mutable.contentType.get() == MediaType.APPLICATION_JSON_TYPE
        new String(mutable.getBody(byte[]).get(), StandardCharsets.UTF_8) == '{"message":"not here"}'
        mutable.getBody(String).get() == '{"message":"not here"}'
        mutable.getBody(Map).get() == [message: 'not here']
        new String(((ByteBodyHttpResponse<?>) mutable).byteBody().split().toByteArray(), StandardCharsets.UTF_8) == '{"message":"not here"}'

        cleanup:
        ((ByteBodyHttpResponse<?>) mutable)?.close()
    }

    void "the mutable copy of a success response keeps the converted body and the bytes"() {
        when:
        HttpResponse<Map> response = client.toBlocking().exchange(HttpRequest.GET('/mutable-response/found'), Map)
        MutableHttpResponse<?> mutable = response.toMutableResponse()

        then:
        mutable.status == HttpStatus.OK
        mutable.body() == [name: 'found']
        mutable.getBody(Map).get() == [name: 'found']
        new String(mutable.getBody(byte[]).get(), StandardCharsets.UTF_8) == '{"name":"found"}'

        when: 'an object body replaces the bytes'
        mutable.body('replaced')

        then:
        !((ByteBodyHttpResponse<?>) mutable).hasByteBody()
        mutable.body() == 'replaced'
        mutable.getBody(String).get() == 'replaced'
        // the original response is not affected
        response.body() == [name: 'found']
    }

    void "the mutable copy outlives the released pooled buffer and does not retain it"() {
        given:
        ByteBuf content = PooledByteBufAllocator.DEFAULT.buffer()
        content.writeCharSequence('{"message":"gone"}', StandardCharsets.UTF_8)
        def source = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND, content)
        source.headers().set(HttpHeaderNames.CONTENT_TYPE, MediaType.APPLICATION_JSON)
        def response = new FullNettyClientHttpResponse(source, server.applicationContext.getBean(MessageBodyHandlerRegistry), null, false, ConversionService.SHARED)

        when:
        MutableHttpResponse<?> mutable = response.toMutableResponse()

        then: 'neither the response nor its mutable copy hold on to the pooled buffer'
        content.refCnt() == 1

        when: 'the client releases the aggregated response'
        source.release()

        then:
        content.refCnt() == 0
        new String(mutable.getBody(byte[]).get(), StandardCharsets.UTF_8) == '{"message":"gone"}'
        mutable.getBody(Map).get() == [message: 'gone']

        when: 'the bytes are closed, the original response still converts its own copy'
        ((ByteBodyHttpResponse<?>) mutable).close()
        ((ByteBodyHttpResponse<?>) mutable).close()

        then:
        new String(response.getBody(byte[]).get(), StandardCharsets.UTF_8) == '{"message":"gone"}'
        // each copy has its own bytes
        def second = response.toMutableResponse()
        new String(((ByteBodyHttpResponse<?>) second).byteBody().split().toByteArray(), StandardCharsets.UTF_8) == '{"message":"gone"}'

        cleanup:
        ((ByteBodyHttpResponse<?>) second)?.close()
    }

    @Requires(property = 'spec.name', value = 'FullNettyClientMutableResponseSpec')
    @Controller('/mutable-response')
    static class MutableResponseController {

        @Get('/missing')
        HttpResponse<String> missing() {
            HttpResponse.<String> notFound().contentType(MediaType.APPLICATION_JSON_TYPE).body('{"message":"not here"}')
        }

        @Get('/found')
        HttpResponse<String> found() {
            HttpResponse.ok('{"name":"found"}').contentType(MediaType.APPLICATION_JSON_TYPE)
        }
    }
}
