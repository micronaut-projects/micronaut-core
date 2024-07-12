package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.type.Argument
import io.micronaut.core.type.MutableHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.body.MessageBodyWriter
import io.micronaut.http.codec.CodecException
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.buffer.ByteBuf
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class ResponseBodySpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'ResponseBodySpec'
    ])

    @AutoCleanup
    HttpClient httpClient = server.applicationContext.createBean(HttpClient, server.getURI())

    def "get a string of empty content type response"() {
        when:
            def response = httpClient.toBlocking().exchange(HttpRequest.GET("/response-body/string"))

        then:
            response.getContentType().isEmpty()
            response.getBody(String).get() == "Hello"
    }

    def "get a number of empty content type response"() {
        when:
            def response = httpClient.toBlocking().exchange(HttpRequest.GET("/response-body/number"))

        then:
            response.getContentType().isEmpty()
            response.getBody(numberClass).get() == numberValue

        where:
            numberClass || numberValue
            BigDecimal  || BigDecimal.valueOf(12345)
            Integer     || Integer.valueOf(12345)
            Double      || Double.valueOf(12345)
    }

    def "get a string after releasing ByteRef of empty content type response"() {
        when:
            def response = httpClient.toBlocking().exchange(HttpRequest.GET("/response-body/string"))
        then:
            response.getContentType().isEmpty()
        when:
            def buffer = response.getBody(ByteBuf).get()
            buffer.release()
        then:
            response.getBody(String).get() == "Hello"
    }

    @Requires(property = 'spec.name', value = 'ResponseBodySpec')
    @Controller("/response-body")
    static class BodyController {

        @Get("/string")
        StringResponseNoContent string() {
            return new StringResponseNoContent()
        }

        @Get("/number")
        NumberResponseNoContent number() {
            return new NumberResponseNoContent()
        }
    }

    static class StringResponseNoContent {}

    static class NumberResponseNoContent {}

    @Requires(property = 'spec.name', value = 'ResponseBodySpec')
    @Singleton
    static class StringResponseNoContentWriter implements MessageBodyWriter<StringResponseNoContent> {

        @Override
        void writeTo(Argument<StringResponseNoContent> type,
                     MediaType mediaType,
                     StringResponseNoContent object,
                     MutableHeaders outgoingHeaders,
                     OutputStream outputStream) throws CodecException {
            // Write response without content type
            outputStream.write("Hello".getBytes(StandardCharsets.UTF_8))
        }
    }

    @Requires(property = 'spec.name', value = 'ResponseBodySpec')
    @Singleton
    static class NumberResponseNoContentWriter implements MessageBodyWriter<NumberResponseNoContent> {

        @Override
        void writeTo(Argument<NumberResponseNoContent> type,
                     MediaType mediaType,
                     NumberResponseNoContent object,
                     MutableHeaders outgoingHeaders,
                     OutputStream outputStream) throws CodecException {
            // Write response without content type
            outputStream.write("12345".getBytes(StandardCharsets.UTF_8))
        }
    }
}
