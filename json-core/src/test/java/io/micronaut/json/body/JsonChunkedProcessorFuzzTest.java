/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.json.body;

import io.micronaut.buffer.netty.NettyReadBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonChunkedProcessorFuzzTest {
    private static final NettyReadBufferFactory READ_BUFFERS = NettyReadBufferFactory.of(ByteBufAllocator.DEFAULT);
    private static final List<String> BUFFER_KINDS = List.of("heap", "direct", "jdk", "offset");

    @Test
    void valuesAndOwnershipSurviveArbitraryChunkBoundaries() throws IOException {
        long seed = Long.getLong("micronaut.json.fuzz.seed", 42L);
        Random random = new Random(seed);
        for (int iteration = 0; iteration < 3000; iteration++) {
            boolean array = random.nextBoolean();
            List<String> expected = new ArrayList<>();
            StringBuilder input = new StringBuilder(array ? " [" : "");
            int count = 1 + random.nextInt(6);
            for (int i = 0; i < count; i++) {
                String value = randomValue(random, 0);
                expected.add(value);
                if (i > 0) {
                    input.append(array ? (random.nextBoolean() ? "," : " ,\r\n") : (random.nextBoolean() ? "\n" : " \t"));
                }
                input.append(value);
            }
            if (array) {
                input.append("]\n");
            }
            byte[] bytes = input.toString().getBytes(StandardCharsets.UTF_8);
            for (String memory : List.of("heap", "direct", "jdk", "offset", "mixed")) {
                JsonChunkedProcessor processor = new JsonChunkedProcessor();
                if (array) {
                    processor.counter.unwrapTopLevelArray();
                }
                List<String> actual = new ArrayList<>();
                List<ByteBuf> pieces = new ArrayList<>();
                for (int offset = 0; offset < bytes.length;) {
                    int length = Math.min(1 + random.nextInt(9), bytes.length - offset);
                    String kind = memory.equals("mixed") ? BUFFER_KINDS.get(random.nextInt(BUFFER_KINDS.size())) : memory;
                    ReadBuffer piece;
                    if (kind.equals("jdk")) {
                        piece = ReadBufferFactory.getJdkFactory().copyOf(ByteBuffer.wrap(bytes, offset, length));
                    } else {
                        ByteBuf buffer;
                        if (kind.equals("offset")) {
                            byte[] backing = new byte[length + 20];
                            System.arraycopy(bytes, offset, backing, 7, length);
                            buffer = Unpooled.wrappedBuffer(backing, 7, length);
                        } else {
                            buffer = kind.equals("direct") ? Unpooled.directBuffer(length) : Unpooled.buffer(length);
                            buffer.writeBytes(bytes, offset, length);
                        }
                        pieces.add(buffer);
                        piece = READ_BUFFERS.adapt(buffer);
                    }
                    processor.feed(piece, value -> collect(value, actual));
                    offset += length;
                }
                processor.finish(value -> collect(value, actual));
                String context = "seed=" + seed + " iteration=" + iteration + " memory=" + memory + " input=" + input;
                assertEquals(expected, actual, context);
                for (ByteBuf buffer : pieces) {
                    assertEquals(0, buffer.refCnt(), context);
                }
            }
        }
    }

    private static void collect(ReadBuffer value, List<String> output) {
        try (value) {
            output.add(value.toString(StandardCharsets.UTF_8));
        }
    }

    private static String randomString(Random random) {
        StringBuilder result = new StringBuilder("\"");
        int length = random.nextInt(8);
        for (int i = 0; i < length; i++) {
            switch (random.nextInt(8)) {
                case 0 -> result.append("\\\"");
                case 1 -> result.append("\\\\");
                case 2 -> result.append("é");
                case 3 -> result.append("😀");
                case 4 -> result.append("]}[{,");
                case 5 -> result.append("\\u00e9");
                case 6 -> result.append("日本");
                default -> result.append((char) ('a' + random.nextInt(26)));
            }
        }
        return result.append('"').toString();
    }

    private static String randomValue(Random random, int depth) {
        return switch (random.nextInt(depth > 2 ? 4 : 6)) {
            case 0 -> randomString(random);
            case 1 -> Long.toString(random.nextLong()) + (random.nextBoolean() ? ".5e-3" : "");
            case 2 -> random.nextBoolean() ? "true" : "false";
            case 3 -> "null";
            case 4 -> {
                StringBuilder result = new StringBuilder("{");
                int count = random.nextInt(4);
                for (int i = 0; i < count; i++) {
                    if (i > 0) {
                        result.append(',');
                    }
                    result.append(randomString(random)).append(':').append(randomValue(random, depth + 1));
                }
                yield result.append('}').toString();
            }
            default -> {
                StringBuilder result = new StringBuilder("[");
                int count = random.nextInt(4);
                for (int i = 0; i < count; i++) {
                    if (i > 0) {
                        result.append(" ,\n");
                    }
                    result.append(randomValue(random, depth + 1));
                }
                yield result.append(']').toString();
            }
        };
    }
}
