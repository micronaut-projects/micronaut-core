package io.micronaut.json.body

import io.micronaut.context.ApplicationContext
import io.micronaut.core.io.buffer.ByteArrayBufferFactory
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.body.ByteBodyFactory
import io.micronaut.json.JsonMapper
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class JsonMessageHandlerWritePieceSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext applicationContext = ApplicationContext.run()

    private static final ByteBodyFactory BODY_FACTORY = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE)

    private String writePiece(Argument<?> type, Object object) {
        def handler = new JsonMessageHandler<Object>(applicationContext.getBean(JsonMapper))
        return handler.writePiece(BODY_FACTORY, HttpRequest.GET("/"), HttpResponse.ok(), type, MediaType.APPLICATION_JSON_TYPE, object)
                .toString(StandardCharsets.UTF_8)
    }

    void "writePiece writes an already serialized document through unchanged"() {
        expect:
        writePiece(Argument.OBJECT_ARGUMENT, '{"foo":"bar"}') == '{"foo":"bar"}'
    }

    void "writePiece serializes using the declared type"() {
        expect: "a declared String is serialized as a JSON string, unlike a declared Object"
        writePiece(Argument.STRING, '{"foo":"bar"}') == '"{\\"foo\\":\\"bar\\"}"'
    }

    void "writePiece and writeTo agree"() {
        given:
        def handler = new JsonMessageHandler<Object>(applicationContext.getBean(JsonMapper))
        def out = new ByteArrayOutputStream()

        when:
        handler.writeTo(type, MediaType.APPLICATION_JSON_TYPE, object, HttpResponse.ok().getHeaders(), out)

        then:
        out.toString(StandardCharsets.UTF_8) == writePiece(type, object)

        where:
        type                     | object
        Argument.OBJECT_ARGUMENT | '{"foo":"bar"}'
        Argument.STRING          | '{"foo":"bar"}'
        Argument.OBJECT_ARGUMENT | 42
        Argument.of(Map)         | [foo: 'bar']
    }
}
