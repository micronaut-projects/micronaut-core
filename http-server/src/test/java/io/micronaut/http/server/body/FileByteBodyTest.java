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
package io.micronaut.http.server.body;

import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableAvailableByteBody;
import io.micronaut.http.body.CloseableByteBody;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A {@link FileByteBody} hands its range over once, or reads it as a stream.
 */
class FileByteBodyTest {
    private static final byte[] CONTENTS = "0123456789abcdefghij".getBytes(StandardCharsets.US_ASCII);
    private static final ByteBodyFactory FACTORY = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);

    private static File file;
    private static ExecutorService executor;

    @BeforeAll
    static void setUp() throws IOException {
        file = File.createTempFile("file-byte-body", ".txt");
        file.deleteOnExit();
        Files.write(file.toPath(), CONTENTS);
        executor = Executors.newCachedThreadPool();
    }

    @AfterAll
    static void tearDown() {
        executor.shutdownNow();
        file.delete();
    }

    private static FileByteBody body(long position, long length) throws IOException {
        return new FileByteBody(new FileInputStream(file), position, length, executor, FACTORY);
    }

    private static String range(int from, int to) {
        return new String(CONTENTS, from, to - from, StandardCharsets.US_ASCII);
    }

    @Test
    void regionIsClaimedOnce() throws IOException {
        FileByteBody body = body(3, 5);
        assertTrue(body.isRegionAvailable());
        assertEquals(OptionalLong.of(5), body.expectedLength());
        FileByteBody.Region region = body.claimRegion();
        try (var channel = region.channel()) {
            assertEquals(3, region.position());
            assertEquals(5, region.length());
            ByteBuffer buffer = ByteBuffer.allocate(5);
            channel.read(buffer, region.position());
            assertEquals(range(3, 8), new String(buffer.array(), StandardCharsets.US_ASCII));
        }
        assertFalse(body.isRegionAvailable());
        assertThrows(IllegalStateException.class, body::claimRegion);
        assertThrows(IllegalStateException.class, body::toInputStream);
        body.close();
    }

    @Test
    void streamReadsTheRange() throws IOException {
        try (FileByteBody body = body(2, 6); InputStream in = body.toInputStream()) {
            assertFalse(body.isRegionAvailable());
            assertEquals(range(2, 8), new String(in.readAllBytes(), StandardCharsets.US_ASCII));
            assertThrows(IllegalStateException.class, body::claimRegion);
        }
    }

    @Test
    void movedBodyKeepsTheRange() throws IOException {
        FileByteBody body = body(4, 3);
        try (CloseableByteBody moved = body.move()) {
            assertFalse(body.isRegionAvailable());
            assertTrue(moved instanceof FileByteBody);
            FileByteBody.Region region = ((FileByteBody) moved).claimRegion();
            region.channel().close();
            assertEquals(4, region.position());
            assertEquals(3, region.length());
        }
        body.close();
    }

    @Test
    void movedStreamIsAStream() throws IOException {
        FileByteBody body = body(0, 4);
        body.allowDiscard();
        body.split().close();
        try (CloseableByteBody moved = body.move()) {
            assertFalse(moved instanceof FileByteBody);
            assertEquals(range(0, 4), new String(moved.toInputStream().readAllBytes(), StandardCharsets.US_ASCII));
        }
        body.close();
    }

    @Test
    void splitReadsTheRangeTwice() throws IOException {
        try (FileByteBody body = body(10, 10); CloseableByteBody copy = body.split(ByteBody.SplitBackpressureMode.FASTEST)) {
            byte[] first = body.toInputStream().readAllBytes();
            byte[] second = copy.toInputStream().readAllBytes();
            assertEquals(range(10, 20), new String(first, StandardCharsets.US_ASCII));
            assertArrayEquals(first, second);
        }
    }

    @Test
    void bufferReadsTheRange() throws Exception {
        try (FileByteBody body = body(5, 5); CloseableAvailableByteBody available = body.buffer().get(10, TimeUnit.SECONDS)) {
            assertEquals(range(5, 10), new String(available.toByteArray(), StandardCharsets.US_ASCII));
        }
    }

    @Test
    void publishersReadTheRange() throws IOException {
        try (FileByteBody body = body(1, 9)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (byte[] bytes : Flux.from(body.toByteArrayPublisher()).collectList().block()) {
                out.write(bytes);
            }
            assertEquals(range(1, 10), out.toString(StandardCharsets.US_ASCII));
        }
        try (FileByteBody body = body(6, 4)) {
            List<ReadBuffer> buffers = Flux.from(body.toReadBufferPublisher()).collectList().block();
            StringBuilder sb = new StringBuilder();
            for (ReadBuffer buffer : buffers) {
                try (buffer) {
                    sb.append(buffer.toString(StandardCharsets.US_ASCII));
                }
            }
            assertEquals(range(6, 10), sb.toString());
        }
        try (FileByteBody body = body(0, 2)) {
            List<ReadBuffer> buffers = Flux.from(body.toUnbufferedReadBufferPublisher()).collectList().block();
            StringBuilder sb = new StringBuilder();
            for (ReadBuffer buffer : buffers) {
                try (buffer) {
                    sb.append(buffer.toString(StandardCharsets.US_ASCII));
                }
            }
            assertEquals(range(0, 2), sb.toString());
        }
    }

    @Test
    void closeReleasesTheFile() throws IOException {
        FileByteBody body = body(0, 1);
        body.close();
        assertFalse(body.isRegionAvailable());
        assertThrows(IllegalStateException.class, body::toInputStream);
        // closing again does nothing
        body.close();
        FileByteBody streamed = body(0, 1);
        streamed.allowDiscard();
        streamed.toInputStream().close();
        streamed.close();
    }

    @Test
    void rangePastTheEndOfTheFileIsEmpty() throws IOException {
        try (FileByteBody body = body(CONTENTS.length + 5, 3); InputStream in = body.toInputStream()) {
            assertEquals(0, in.readAllBytes().length);
            assertEquals(-1, in.read());
        }
    }

    @Test
    void singleByteReadsStopAtTheEndOfTheRange() throws IOException {
        try (FileByteBody body = body(7, 2); InputStream in = body.toInputStream()) {
            assertEquals(CONTENTS[7], in.read());
            assertEquals(CONTENTS[8], in.read());
            assertEquals(-1, in.read());
        }
    }
}
