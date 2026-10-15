package io.micronaut.http.body;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MediaType;
import io.micronaut.http.simple.SimpleHttpHeaders;
import io.micronaut.runtime.ApplicationConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Flux;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code Accept-Charset} describes the response the client wants, so it must not influence how a
 * request body is decoded (RFC 9110 section 12.5.2).
 */
class RequestBodyCharsetTest {
    private static final String UTF8_JSON = "{\"name\":\"한글 테스트 é €\"}";
    private static final String LATIN1_TEXT = "café";
    private static final MediaType LATIN1_JSON = MediaType.of("application/json; charset=ISO-8859-1");
    private static final MediaType LATIN1_TEXT_PLAIN = MediaType.of("text/plain; charset=ISO-8859-1");

    private final StringBodyReader stringReader = new StringBodyReader(new ApplicationConfiguration());
    private final TextPlainObjectBodyReader<String> textPlainReader = new TextPlainObjectBodyReader<>(new ApplicationConfiguration(), ConversionService.SHARED);

    @ParameterizedTest
    @ValueSource(strings = {"Big5", "ISO-8859-1"})
    void stringBufferIgnoresAcceptCharset(String acceptCharset) {
        String body = stringReader.read(Argument.STRING, MediaType.APPLICATION_JSON_TYPE, acceptCharset(acceptCharset), utf8Buffer(UTF8_JSON));

        assertEquals(UTF8_JSON, body);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Big5", "ISO-8859-1"})
    void stringInputStreamIgnoresAcceptCharset(String acceptCharset) {
        String body = stringReader.read(Argument.STRING, MediaType.APPLICATION_JSON_TYPE, acceptCharset(acceptCharset), new ByteArrayInputStream(UTF8_JSON.getBytes(StandardCharsets.UTF_8)));

        assertEquals(UTF8_JSON, body);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Big5", "ISO-8859-1"})
    void stringChunksIgnoreAcceptCharset(String acceptCharset) {
        List<String> chunks = Flux.from(stringReader.readChunked(Argument.STRING, MediaType.APPLICATION_JSON_TYPE, acceptCharset(acceptCharset), Flux.just(utf8Buffer(UTF8_JSON))))
            .collectList()
            .block();

        assertEquals(List.of(UTF8_JSON), chunks);
    }

    @Test
    void stringWithoutMediaTypeIgnoresAcceptCharset() {
        String body = stringReader.read(Argument.STRING, null, acceptCharset("Big5"), utf8Buffer(UTF8_JSON));

        assertEquals(UTF8_JSON, body);
    }

    @Test
    void stringUsesCharsetOfContentType() {
        String body = stringReader.read(Argument.STRING, LATIN1_JSON, acceptCharset("UTF-8"), latin1Buffer(LATIN1_TEXT));

        assertEquals(LATIN1_TEXT, body);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Big5", "ISO-8859-1"})
    void textPlainIgnoresAcceptCharset(String acceptCharset) {
        String body = textPlainReader.read(Argument.STRING, MediaType.TEXT_PLAIN_TYPE, acceptCharset(acceptCharset), utf8Buffer(UTF8_JSON));

        assertEquals(UTF8_JSON, body);
    }

    @Test
    void textPlainUsesCharsetOfContentType() {
        String body = textPlainReader.read(Argument.STRING, LATIN1_TEXT_PLAIN, acceptCharset("UTF-8"), latin1Buffer(LATIN1_TEXT));

        assertEquals(LATIN1_TEXT, body);
    }

    private static SimpleHttpHeaders acceptCharset(String charset) {
        SimpleHttpHeaders headers = new SimpleHttpHeaders();
        headers.add(HttpHeaders.ACCEPT_CHARSET, charset);
        return headers;
    }

    private static ByteBuffer<?> utf8Buffer(String text) {
        return buffer(text, StandardCharsets.UTF_8);
    }

    private static ByteBuffer<?> latin1Buffer(String text) {
        return buffer(text, StandardCharsets.ISO_8859_1);
    }

    private static ByteBuffer<?> buffer(String text, Charset charset) {
        return ByteArrayBufferFactory.INSTANCE.wrap(text.getBytes(charset));
    }
}
