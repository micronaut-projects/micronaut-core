package io.micronaut.json.body;

import io.micronaut.buffer.netty.NettyByteBufferFactory;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.stream.ByteBodyElements;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.simple.SimpleHttpHeaders;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.JsonSyntaxException;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Signal;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The elements of a JSON body read through the cursor: the values before a failure are
 * delivered, as the reactive reader delivers them, and a JSON null is not an element.
 */
class JsonBodyElementsTest {
    private static final JsonMapper MAPPER = JsonMapper.createDefault();
    private static final ByteBodyFactory BODIES = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);

    @Test
    void aNullElementFailsInsteadOfEndingTheElements() {
        try (ByteBodyElements<Integer> elements = elements("[1,null,2]")) {
            assertEquals(Optional.of(1), next(elements));
            CompletionException failure = assertThrows(CompletionException.class, () -> next(elements));
            assertInstanceOf(CodecException.class, failure.getCause());
        }
    }

    @Test
    void theValuesBeforeAMalformedEndAreDelivered() {
        try (ByteBodyElements<Integer> elements = elements("[1,2,3")) {
            assertEquals(Optional.of(1), next(elements));
            assertEquals(Optional.of(2), next(elements));
            CompletionException failure = assertThrows(CompletionException.class, () -> next(elements));
            assertInstanceOf(JsonSyntaxException.class, failure.getCause());
        }
    }

    @Test
    void theValuesBeforeAMalformedValueAreDelivered() {
        try (ByteBodyElements<Integer> elements = elements("[1,2,}")) {
            assertEquals(Optional.of(1), next(elements));
            assertEquals(Optional.of(2), next(elements));
            CompletionException failure = assertThrows(CompletionException.class, () -> next(elements));
            assertInstanceOf(JsonSyntaxException.class, failure.getCause());
        }
    }

    @Test
    void theReactiveReaderEmitsTheValuesBeforeAMalformedValueThenFails() {
        List<Signal<Integer>> signals = Flux.from(new JsonMessageHandler<Integer>(MAPPER).readChunked(Argument.of(Integer.class), MediaType.APPLICATION_JSON_TYPE,
                new SimpleHttpHeaders(), Flux.just(NettyByteBufferFactory.DEFAULT.wrap(Unpooled.copiedBuffer("[1,2,}", StandardCharsets.UTF_8))), 1024))
            .materialize()
            .collectList()
            .block();
        assertEquals(List.of(1, 2), signals.stream().filter(Signal::isOnNext).map(Signal::get).toList());
        assertInstanceOf(JsonSyntaxException.class, signals.getLast().getThrowable());
    }

    @Test
    void theReactiveReaderFailsOnANullElement() {
        List<Signal<Integer>> signals = Flux.from(new JsonMessageHandler<Integer>(MAPPER).readChunked(Argument.of(Integer.class), MediaType.APPLICATION_JSON_TYPE,
                new SimpleHttpHeaders(), Flux.just(NettyByteBufferFactory.DEFAULT.wrap(Unpooled.copiedBuffer("[1,null,2]", StandardCharsets.UTF_8))), 1024))
            .materialize()
            .collectList()
            .block();
        assertEquals(List.of(1), signals.stream().filter(Signal::isOnNext).map(Signal::get).toList());
        assertInstanceOf(CodecException.class, signals.getLast().getThrowable());
    }

    private static ByteBodyElements<Integer> elements(String json) {
        return new ByteBodyElements<>(BODIES.copyOf(json, StandardCharsets.UTF_8),
            new JsonMessageHandler<Integer>(MAPPER).openPieceReader(Argument.of(Integer.class), MediaType.APPLICATION_JSON_TYPE, new SimpleHttpHeaders(), 1024),
            e -> e);
    }

    private static Optional<Integer> next(ByteBodyElements<Integer> elements) {
        return elements.next().toCompletableFuture().join();
    }
}
