package io.micronaut.http.netty.body;

import io.micronaut.buffer.netty.NettyByteBufferFactory;
import io.micronaut.buffer.netty.NettyReadBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.PieceReader;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.exceptions.ContentLengthExceededException;
import io.micronaut.http.simple.SimpleHttpHeaders;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.JsonSyntaxException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The {@link PieceReader} of the Netty JSON handlers: the elements of a JSON array or a JSON
 * stream, split from the pieces as they are read and decoded when they are polled, without
 * Reactive Streams.
 */
class JsonPieceReaderTest {

    private static final JsonMapper MAPPER = JsonMapper.createDefault();
    private static final Argument<Book> BOOK = Argument.of(Book.class);
    private static final NettyReadBufferFactory READ_BUFFERS = NettyReadBufferFactory.of(ByteBufAllocator.DEFAULT);

    @Test
    void theElementsOfAnArraySplitBetweenPieces() throws IOException {
        PieceReader<Book> reader = arrayReader(BOOK, 1024);

        reader.read(piece("[{\"title\":\"The St"));
        assertNull(reader.poll());
        reader.read(piece("and\"},{\"title\":\"It\"}"));
        assertEquals(new Book("The Stand"), reader.poll());
        assertEquals(new Book("It"), reader.poll());
        assertNull(reader.poll());
        reader.read(piece("]"));
        reader.complete();
        assertNull(reader.poll());
        reader.close();
    }

    @Test
    void theValuesOfAStream() throws IOException {
        PieceReader<Book> reader = new NettyJsonStreamHandler<Book>(MAPPER)
            .openPieceReader(BOOK, MediaType.APPLICATION_JSON_STREAM_TYPE, new SimpleHttpHeaders(), 1024);

        reader.read(piece("{\"title\":\"The Stand\"}\n{\"title\":"));
        assertEquals(new Book("The Stand"), reader.poll());
        assertNull(reader.poll());
        reader.read(piece("\"It\"}\n"));
        reader.complete();
        assertEquals(new Book("It"), reader.poll());
        assertNull(reader.poll());
    }

    @Test
    void aNumberAtTheEndOfAStreamIsCompletedByTheEnd() throws IOException {
        PieceReader<Integer> reader = new NettyJsonStreamHandler<Integer>(MAPPER)
            .openPieceReader(Argument.INT, MediaType.APPLICATION_JSON_STREAM_TYPE, new SimpleHttpHeaders(), 1024);

        reader.read(piece("1 2 3"));
        List<Integer> numbers = drain(reader);
        reader.complete();
        numbers.addAll(drain(reader));

        assertEquals(List.of(1, 2, 3), numbers);
    }

    @Test
    void anElementIsDecodedWhenItIsPolled() throws IOException {
        PieceReader<Book> reader = arrayReader(BOOK, 1024);

        // both elements are complete, the second does not decode as a book
        reader.read(piece("[{\"title\":\"It\"},{\"title\":[1,2]}]"));
        assertEquals(new Book("It"), reader.poll());
        // the decoding failure of the reader, as for the publisher of the elements
        assertThrows(RuntimeException.class, reader::poll);
        reader.close();
    }

    @Test
    void eachElementIsLimited() throws IOException {
        PieceReader<Map<String, Object>> reader = arrayReader(Argument.mapOf(String.class, Object.class), 10);

        reader.read(piece("[{\"a\":1},"));
        assertThrows(ContentLengthExceededException.class, () -> reader.read(piece("{\"b\":\"too large\"}]")));
        assertEquals(Map.of("a", 1), reader.poll());
        reader.close();
    }

    @Test
    void malformedInputFailsWithAJsonSyntaxException() {
        PieceReader<Book> reader = new NettyJsonStreamHandler<Book>(MAPPER)
            .openPieceReader(BOOK, MediaType.APPLICATION_JSON_STREAM_TYPE, new SimpleHttpHeaders(), 1024);

        assertThrows(JsonSyntaxException.class, () -> reader.read(piece("﻿{}")));
        reader.close();
    }

    @Test
    void closingReleasesWhatIsHeld() throws IOException {
        PieceReader<Book> reader = arrayReader(BOOK, 1024);
        ByteBuf complete = buffer("[{\"title\":\"It\"},");
        ByteBuf partial = buffer("{\"title\":\"The St");

        reader.read(READ_BUFFERS.adapt(complete));
        reader.read(READ_BUFFERS.adapt(partial));
        // an element that was not polled, and a partial one
        reader.close();
        reader.close();

        assertEquals(0, complete.refCnt());
        assertEquals(0, partial.refCnt());
        assertNull(reader.poll());
    }

    @Test
    void aStreamBoundAsAListIsReadWithoutAPublisher() {
        NettyJsonStreamHandler<List<Book>> handler = new NettyJsonStreamHandler<>(MAPPER);
        ByteBuf input = buffer("{\"title\":\"The Stand\"}\n{\"title\":\"It\"}\n");

        List<Book> books = handler.read(Argument.listOf(Book.class), MediaType.APPLICATION_JSON_STREAM_TYPE, new SimpleHttpHeaders(),
            NettyByteBufferFactory.DEFAULT.wrap(input));

        assertEquals(List.of(new Book("The Stand"), new Book("It")), books);
        assertEquals(0, input.refCnt());
    }

    @Test
    void aMalformedStreamBoundAsAListFails() {
        NettyJsonStreamHandler<List<Book>> handler = new NettyJsonStreamHandler<>(MAPPER);
        ByteBuf input = buffer("{\"title\":\"It\"}\n{\"title\"");

        assertThrows(CodecException.class, () -> handler.read(Argument.listOf(Book.class), MediaType.APPLICATION_JSON_STREAM_TYPE, new SimpleHttpHeaders(),
            NettyByteBufferFactory.DEFAULT.wrap(input)));
        assertEquals(0, input.refCnt());
    }

    private static <T> PieceReader<T> arrayReader(Argument<T> type, long maxElementSize) {
        return new NettyJsonHandler<T>(MAPPER).openPieceReader(type, MediaType.APPLICATION_JSON_TYPE, new SimpleHttpHeaders(), maxElementSize);
    }

    private static <T> List<T> drain(PieceReader<T> reader) throws IOException {
        List<T> elements = new ArrayList<>();
        T element;
        while ((element = reader.poll()) != null) {
            elements.add(element);
        }
        return elements;
    }

    private static ReadBuffer piece(String text) {
        return READ_BUFFERS.adapt(buffer(text));
    }

    private static ByteBuf buffer(String text) {
        return Unpooled.copiedBuffer(text, StandardCharsets.UTF_8);
    }

    record Book(String title) {
    }
}
