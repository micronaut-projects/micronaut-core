package io.micronaut.http.server.netty.body

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.convert.MutableConversionService
import io.micronaut.core.convert.TypeConverterRegistrar
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.buffer.ByteBuf
import jakarta.inject.Singleton
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class BodyConversionSpec extends Specification {
    static final String WEIRD_CONTENT_TYPE = "application/x-weird"

    def 'weird content type, object param'() {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'BodyConversionSpec'])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = ctx.createBean(HttpClient, server.URI).toBlocking()

        when:
        def response = client.retrieve(HttpRequest.POST('/body-conversion/weird-object', "foo")
                .contentType(WEIRD_CONTENT_TYPE), String)
        then:
        response == 'body: foo'

        cleanup:
        client.close()
        server.stop()
    }

    def 'weird content type, converted param'() {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'BodyConversionSpec'])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = ctx.createBean(HttpClient, server.URI).toBlocking()

        when:
        def response = client.retrieve(HttpRequest.POST('/body-conversion/weird-converted', "foo")
                .contentType(WEIRD_CONTENT_TYPE), String)
        then:
        response == 'body: MyRecord[s=foo]'

        cleanup:
        client.close()
        server.stop()
    }

    @Controller("/body-conversion")
    @Requires(property = "spec.name", value = "BodyConversionSpec")
    static class MyCtrl {
        @Consumes(WEIRD_CONTENT_TYPE)
        @Post("/weird-object")
        String object(@Body Object o) {
            def text = ((ByteBuf) o).toString(StandardCharsets.UTF_8)
            o.release()
            return "body: " + text
        }

        @Consumes(WEIRD_CONTENT_TYPE)
        @Post("/weird-converted")
        String converted(@Body MyRecord o) {
            return "body: " + o
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "BodyConversionSpec")
    static class MyConverter implements TypeConverterRegistrar {
        @Override
        void register(MutableConversionService conversionService) {
            conversionService.addConverter(ByteBuf.class, MyRecord.class, buf -> {
                return new MyRecord(buf.toString(StandardCharsets.UTF_8))
            })
        }
    }

    record MyRecord(String s) {
    }
}
