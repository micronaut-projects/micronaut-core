package io.micronaut.json.body;

import io.micronaut.buffer.netty.NettyReadBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The values of an input split into pieces of every size, of heap, direct and JDK buffers: the
 * values are the same, and every piece is released once.
 */
class JsonChunkedPiecesTest {
    private static final NettyReadBufferFactory READ_BUFFERS = NettyReadBufferFactory.of(ByteBufAllocator.DEFAULT);
    private static final String ARRAY = "[ {\"a\":\"x,y}\"} , {\"b\":[1,2,{\"c\":\"\\\"q\\\"\"}]},\"s\",42 ,{\"d\":{}}, true]";
    private static final List<String> ELEMENTS = List.of("{\"a\":\"x,y}\"}", "{\"b\":[1,2,{\"c\":\"\\\"q\\\"\"}]}", "\"s\"", "42", "{\"d\":{}}", "true");
    private static final String STREAM = "{\"a\":1}\n\"two\" 3\n[4,5]  {\"b\":\"}\"}\n6";
    private static final List<String> VALUES = List.of("{\"a\":1}", "\"two\"", "3", "[4,5]", "{\"b\":\"}\"}", "6");

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 5, 7, 11, 1000})
    void theElementsOfAnArray(int pieceSize) throws IOException {
        for (String memory : List.of("heap", "direct", "jdk")) {
            JsonChunkedProcessor processor = new JsonChunkedProcessor();
            processor.counter.unwrapTopLevelArray();
            assertEquals(ELEMENTS, split(processor, ARRAY, pieceSize, memory), memory);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 5, 7, 11, 1000})
    void theValuesOfAStream(int pieceSize) throws IOException {
        for (String memory : List.of("heap", "direct", "jdk")) {
            assertEquals(VALUES, split(new JsonChunkedProcessor(), STREAM, pieceSize, memory), memory);
        }
    }

    private static List<String> split(JsonChunkedProcessor processor, String input, int pieceSize, String memory) throws IOException {
        byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
        List<String> values = new ArrayList<>();
        List<ByteBuf> nettyPieces = new ArrayList<>();
        for (int i = 0; i < bytes.length; i += pieceSize) {
            int length = Math.min(pieceSize, bytes.length - i);
            ReadBuffer piece;
            if ("jdk".equals(memory)) {
                piece = ReadBufferFactory.getJdkFactory().copyOf(java.nio.ByteBuffer.wrap(bytes, i, length));
            } else {
                ByteBuf buf = "direct".equals(memory) ? Unpooled.directBuffer(length) : Unpooled.buffer(length);
                buf.writeBytes(bytes, i, length);
                nettyPieces.add(buf);
                piece = READ_BUFFERS.adapt(buf);
            }
            processor.feed(piece, value -> values.add(value.toString(StandardCharsets.UTF_8)));
        }
        processor.finish(value -> values.add(value.toString(StandardCharsets.UTF_8)));
        for (ByteBuf piece : nettyPieces) {
            assertEquals(0, piece.refCnt());
        }
        return values;
    }
}
