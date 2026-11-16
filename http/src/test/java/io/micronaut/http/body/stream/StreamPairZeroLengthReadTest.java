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
package io.micronaut.http.body.stream;

import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A zero-length read of a side of a split {@link InputStreamByteBody} must return 0 without
 * waiting for the other side, so that {@link InputStream#readNBytes(int)} does not hang.
 */
class StreamPairZeroLengthReadTest {

    private static final byte[] FOO = "foo".getBytes(StandardCharsets.UTF_8);

    @Test
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void slowestZeroLengthReadDoesNotWaitForTheOtherSide() throws Exception {
        try (PipedOutputStream out = new PipedOutputStream();
             CloseableByteBody body = body(new PipedInputStream(out));
             CloseableByteBody split = body.split(ByteBody.SplitBackpressureMode.SLOWEST)) {
            InputStream left = body.toInputStream();
            InputStream right = split.toInputStream();
            assertEquals(0, left.read(new byte[1], 0, 0));
            assertEquals(0, right.read(new byte[1], 1, 0));
            assertThrows(IndexOutOfBoundsException.class, () -> left.read(new byte[1], 1, 1));
        }
    }

    @Test
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void slowestReadNBytesDoesNotHang() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (PipedOutputStream out = new PipedOutputStream();
             CloseableByteBody body = body(new PipedInputStream(out));
             CloseableByteBody split = body.split(ByteBody.SplitBackpressureMode.SLOWEST)) {
            InputStream left = body.toInputStream();
            InputStream right = split.toInputStream();
            out.write(FOO);
            out.flush();
            CompletableFuture<byte[]> l = CompletableFuture.supplyAsync(() -> readNBytes(left), executor);
            CompletableFuture<byte[]> r = CompletableFuture.supplyAsync(() -> readNBytes(right), executor);
            assertArrayEquals(FOO, l.get(5, TimeUnit.SECONDS));
            assertArrayEquals(FOO, r.get(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void listeningReadNBytesDoesNotHang() throws Exception {
        try (PipedOutputStream out = new PipedOutputStream();
             CloseableByteBody body = body(new PipedInputStream(out));
             CloseableByteBody split = body.split(ByteBody.SplitBackpressureMode.ORIGINAL)) {
            InputStream preferred = body.toInputStream();
            InputStream listening = split.toInputStream();
            assertEquals(0, listening.read(new byte[1], 0, 0));
            out.write(FOO);
            out.flush();
            assertArrayEquals(FOO, preferred.readNBytes(3));
            assertArrayEquals(FOO, listening.readNBytes(3));
            assertThrows(IndexOutOfBoundsException.class, () -> listening.read(new byte[1], 1, 1));
        }
    }

    private static CloseableByteBody body(InputStream stream) {
        return InputStreamByteBody.create(stream, OptionalLong.empty(), Runnable::run, ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE));
    }

    private static byte[] readNBytes(InputStream stream) {
        try {
            return stream.readNBytes(3);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
