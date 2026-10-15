package io.micronaut.json.body

import io.micronaut.core.io.buffer.ByteArrayBufferFactory
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.body.ByteBodyFactory
import io.micronaut.json.JsonMapper
import io.micronaut.json.JsonStreamConfig
import io.micronaut.json.tree.JsonNode
import spock.lang.Specification

import java.nio.charset.StandardCharsets

/**
 * A mapper without a native stream writer writes each piece with writeValue, and a writeValue
 * implementation commonly closes the stream it was given once the value is written. The pieces
 * after that must still carry their bytes.
 */
class JsonPieceWriterSpec extends Specification {

    void "a mapper that closes the stream after each value still writes every piece"() {
        given:
        def bodyFactory = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE)
        def handler = new JsonMessageHandler<Integer>(new ClosingMapper())
        def separator = bodyFactory.readBufferFactory().copyOf(",", StandardCharsets.UTF_8)

        when:
        def writer = handler.openPieceWriter(bodyFactory, HttpRequest.GET("/"), HttpResponse.ok(), Argument.INT, MediaType.APPLICATION_JSON_TYPE)
        def first = writer.writePiece(null, 42)
        def second = writer.writePiece(separator, 43)
        def third = writer.writePiece(separator, 44)

        then:
        text(first) == "42"
        text(second) == ",43"
        text(third) == ",44"

        cleanup:
        writer.close()
        separator.close()
    }

    private static String text(def body) {
        try {
            return body.toInputStream().text
        } finally {
            body.close()
        }
    }

    /**
     * Writes the value with toString and then closes the stream, as a mapper backed by a JSON
     * library that auto-closes its target does.
     */
    static class ClosingMapper implements JsonMapper {
        @Override
        <T> void writeValue(OutputStream outputStream, Argument<T> type, T object) throws IOException {
            outputStream.write(String.valueOf(object).getBytes(StandardCharsets.UTF_8))
            outputStream.close()
        }

        @Override
        <T> T readValueFromTree(JsonNode tree, Argument<T> type) throws IOException {
            throw new UnsupportedOperationException()
        }

        @Override
        <T> T readValue(InputStream inputStream, Argument<T> type) throws IOException {
            throw new UnsupportedOperationException()
        }

        @Override
        <T> T readValue(byte[] byteArray, Argument<T> type) throws IOException {
            throw new UnsupportedOperationException()
        }

        @Override
        JsonNode writeValueToTree(Object value) throws IOException {
            throw new UnsupportedOperationException()
        }

        @Override
        <T> JsonNode writeValueToTree(Argument<T> type, T value) throws IOException {
            throw new UnsupportedOperationException()
        }

        @Override
        void writeValue(OutputStream outputStream, Object object) throws IOException {
            throw new UnsupportedOperationException()
        }

        @Override
        byte[] writeValueAsBytes(Object object) throws IOException {
            throw new UnsupportedOperationException()
        }

        @Override
        <T> byte[] writeValueAsBytes(Argument<T> type, T object) throws IOException {
            throw new UnsupportedOperationException()
        }

        @Override
        JsonStreamConfig getStreamConfig() {
            return JsonStreamConfig.DEFAULT
        }
    }
}
