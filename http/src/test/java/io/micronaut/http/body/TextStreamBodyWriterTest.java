package io.micronaut.http.body;

import io.micronaut.core.type.Argument;
import io.micronaut.core.type.GenericArgument;
import io.micronaut.http.MediaType;
import io.micronaut.http.simple.SimpleHttpHeaders;
import io.micronaut.http.sse.Event;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextStreamBodyWriterTest {

    static Stream<Arguments> events() {
        return Stream.of(
            Arguments.of(Event.of("a"), "data: a\n\n"),
            Arguments.of(Event.of("a\nb"), "data: a\ndata: b\n\n"),
            Arguments.of(Event.of("a\rb"), "data: a\ndata: b\n\n"),
            Arguments.of(Event.of("a\r\nb"), "data: a\ndata: b\n\n"),
            Arguments.of(Event.of("a\r\rid: x\n\ndata: b"), "data: a\ndata: \ndata: id: x\ndata: \ndata: data: b\n\n"),
            Arguments.of(Event.of("a\n"), "data: a\ndata: \n\n"),
            Arguments.of(Event.of(""), "\n"),
            Arguments.of(Event.of("").comment("keep-alive"), ": keep-alive\n\n"),
            Arguments.of(Event.of("\n"), "data: \ndata: \n\n"),
            Arguments.of(Event.of("a").id("1\r\nevent: x\n\0"), "id: 1event: x\ndata: a\n\n"),
            Arguments.of(Event.of("a").name("foo\rdata: x\n\0"), "event: foodata: x\0\ndata: a\n\n"),
            Arguments.of(Event.of("a").comment("c1\nid: x\r\nc3\r"), ": c1\n: id: x\n: c3\n: \ndata: a\n\n")
        );
    }

    @ParameterizedTest
    @MethodSource("events")
    void writeEvent(Event<String> event, String expected) {
        TextStreamBodyWriter<Event<String>> writer = new TextStreamBodyWriter<>(MessageBodyHandlerRegistry.EMPTY);
        Argument<Event<String>> type = new GenericArgument<>() { };

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        writer.writeTo(type, MediaType.TEXT_EVENT_STREAM_TYPE, event, new SimpleHttpHeaders(), stream);
        assertEquals(expected, stream.toString(StandardCharsets.UTF_8));
    }
}
