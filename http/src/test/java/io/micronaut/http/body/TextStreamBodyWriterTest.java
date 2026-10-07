package io.micronaut.http.body;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.simple.SimpleHttpHeaders;
import io.micronaut.http.sse.Event;
import okhttp3.internal.sse.ServerSentEventReader;
import okio.Buffer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The events the writer writes, read back by an independent parser of server-sent events, the
 * one of OkHttp, as a client reads them.
 */
class TextStreamBodyWriterTest {

    private static ApplicationContext context;

    @BeforeAll
    static void start() {
        context = ApplicationContext.run();
    }

    @AfterAll
    static void stop() {
        context.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"one", "a\nb", "a\rb", "a\r\nb", "a\n", "a\r", "\nb", "a\n\nb", "a\r\rb", "a\n\r\nb", "\r\n", ""})
    void theDataIsReadAsItWasWritten(String data) throws IOException {
        // a client joins the lines of the data with LF, whichever line break they ended with
        String lines = data.replace("\r\n", "\n").replace('\r', '\n');
        assertEquals(List.of(new ReadEvent(null, null, lines)), read(write(Event.of(data))));
    }

    @Test
    void anEventWithEmptyDataIsReceived() throws IOException {
        // without a data line, a client ignores the event
        assertEquals(List.of(new ReadEvent("1", "ping", "")), read(write(Event.of("").id("1").name("ping"))));
    }

    @Test
    void everyLineOfACommentIsAComment() throws IOException {
        Event<String> event = Event.of("data").comment("first\nid: injected\revent: injected\r\ndata: injected");
        assertEquals(List.of(new ReadEvent(null, null, "data")), read(write(event)));
    }

    @Test
    void aLineBreakInTheIdStartsNoOtherField() throws IOException {
        Event<String> event = Event.of("data").id("1\nevent: injected\r\ndata: injected\r");
        assertEquals(List.of(new ReadEvent("1event: injecteddata: injected", null, "data")), read(write(event)));
    }

    @Test
    void aLineBreakInTheNameStartsNoOtherField() throws IOException {
        Event<String> event = Event.of("data").name("name\r\n\r\ndata: injected");
        assertEquals(List.of(new ReadEvent(null, "namedata: injected", "data")), read(write(event)));
    }

    @Test
    void aJsonEventIsReadAsItWasWritten() throws IOException {
        Event<Map<String, String>> event = Event.of(Map.of("text", "a\nb")).id("7").name("json").retry(Duration.ofSeconds(1));
        assertEquals(List.of(new ReadEvent("7", "json", "{\"text\":\"a\\nb\"}")), read(write(event)));
    }

    @Test
    void theWireFormatOfASingleLineEventIsUnchanged() throws IOException {
        assertEquals("id: 1\nevent: e\ndata: one\n\n", new String(write(Event.of("one").id("1").name("e")), StandardCharsets.UTF_8));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static byte[] write(Event<?> event) {
        Argument<Object> type = (Argument) Argument.of(Event.class, event.getData().getClass());
        MessageBodyWriter<Object> writer = context.getBean(MessageBodyHandlerRegistry.class)
            .getWriter(type, List.of(MediaType.TEXT_EVENT_STREAM_TYPE));
        MutableHttpHeaders headers = new SimpleHttpHeaders();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.writeTo(type, MediaType.TEXT_EVENT_STREAM_TYPE, event, headers, out);
        return out.toByteArray();
    }

    private static List<ReadEvent> read(byte[] stream) throws IOException {
        List<ReadEvent> events = new ArrayList<>();
        ServerSentEventReader reader = new ServerSentEventReader(new Buffer().write(stream), new ServerSentEventReader.Callback() {
            @Override
            public void onEvent(String id, String type, String data) {
                events.add(new ReadEvent(id, type, data));
            }

            @Override
            public void onRetryChange(long timeMs) {
            }
        });
        while (reader.processNextEvent()) {
            // reads the next event
        }
        return events;
    }

    private record ReadEvent(String id, String type, String data) {
    }
}
