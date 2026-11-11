package io.micronaut.json

import io.micronaut.core.type.Argument
import io.micronaut.json.tree.JsonNode
import spock.lang.Specification

import java.nio.charset.StandardCharsets

/**
 * A mapper that does not implement {@link JsonMapper#createStreamWriter} natively gets a writer
 * that calls {@link JsonMapper#writeValue(OutputStream, Argument, Object)} for every value.
 */
class JsonMapperStreamWriterSpec extends Specification {

    void "the default stream writer writes each value with writeValue and nothing in between"() {
        given:
        def mapper = new WriteValueOnlyMapper()
        def out = new ByteArrayOutputStream()

        when:
        def writer = mapper.createStreamWriter(out, Argument.STRING)
        writer.write("a")
        writer.write("bc")
        writer.write(null)

        then:
        out.toString(StandardCharsets.UTF_8) == '"a""bc"null'
        mapper.calls == [Argument.STRING, Argument.STRING, Argument.STRING]

        when:
        writer.close()

        then:
        out.closed
    }

    void "the default stream writer keeps the stream open for a mapper that closes it after each value"() {
        given:
        def mapper = new WriteValueOnlyMapper(closeAfterWrite: true)
        def out = new ClosingEnforcedOutputStream()

        when:
        def writer = mapper.createStreamWriter(out, Argument.STRING)
        writer.write("a")
        writer.write("b")

        then:
        out.toString(StandardCharsets.UTF_8) == '"a""b"'
        !out.closed

        when:
        writer.close()

        then:
        out.closed
    }

    void "the default stream writer rejects null arguments"() {
        when:
        new WriteValueOnlyMapper().createStreamWriter(null, Argument.STRING)

        then:
        thrown(NullPointerException)

        when:
        new WriteValueOnlyMapper().createStreamWriter(new ByteArrayOutputStream(), null)

        then:
        thrown(NullPointerException)
    }

    static class ByteArrayOutputStream extends java.io.ByteArrayOutputStream {
        boolean closed

        @Override
        void close() {
            closed = true
        }
    }

    /**
     * Fails writes once closed, like a socket or file stream.
     */
    static class ClosingEnforcedOutputStream extends java.io.ByteArrayOutputStream {
        boolean closed

        @Override
        void write(int b) {
            ensureOpen()
            super.write(b)
        }

        @Override
        void write(byte[] b, int off, int len) {
            ensureOpen()
            super.write(b, off, len)
        }

        @Override
        void close() {
            closed = true
        }

        private void ensureOpen() {
            if (closed) {
                throw new IOException("closed")
            }
        }
    }

    /**
     * A mapper that only knows how to write a string or null value.
     */
    static class WriteValueOnlyMapper implements JsonMapper {
        List<Argument<?>> calls = []
        boolean closeAfterWrite

        @Override
        <T> void writeValue(OutputStream outputStream, Argument<T> type, T object) throws IOException {
            calls.add(type)
            outputStream.write((object == null ? 'null' : '"' + object + '"').getBytes(StandardCharsets.UTF_8))
            if (closeAfterWrite) {
                // like a mapper whose generator closes its target
                outputStream.close()
            }
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
