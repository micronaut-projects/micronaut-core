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

import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.core.publisher.Sinks;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A zero-length read must return 0 without waiting for data, as the {@link InputStream}
 * contract requires; otherwise {@link InputStream#readNBytes(int)} hangs once it has read
 * exactly the requested bytes of a body that has not ended yet.
 */
class PublisherAsStreamZeroLengthReadTest {

    @Test
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void zeroLengthReadDoesNotBlock() throws Exception {
        Sinks.Many<ReadBuffer> sink = Sinks.many().unicast().onBackpressureBuffer();
        PublisherAsBlocking blocking = new PublisherAsBlocking();
        sink.asFlux().subscribe(blocking);
        InputStream stream = new PublisherAsStream(blocking);

        assertEquals(0, stream.read(new byte[4], 0, 0));
    }

    @Test
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void readNBytesReturnsWithoutTheEndOfTheBody() throws Exception {
        Sinks.Many<ReadBuffer> sink = Sinks.many().unicast().onBackpressureBuffer();
        PublisherAsBlocking blocking = new PublisherAsBlocking();
        sink.asFlux().subscribe(blocking);
        InputStream stream = new PublisherAsStream(blocking);

        sink.tryEmitNext(ReadBufferFactory.getJdkFactory().copyOf("abcd", StandardCharsets.UTF_8));

        assertArrayEquals("abcd".getBytes(StandardCharsets.UTF_8), stream.readNBytes(4));
    }

    @Test
    void invalidBoundsAreRejected() {
        InputStream stream = new PublisherAsStream(new PublisherAsBlocking());

        assertThrows(IndexOutOfBoundsException.class, () -> stream.read(new byte[4], 3, 2));
    }
}
