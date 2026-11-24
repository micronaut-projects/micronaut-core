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
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.ReplayableByteBody;
import io.micronaut.http.simple.SimpleHttpHeaders;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A body that {@link ByteBodyFactory} wraps and that is closed before it is read releases the
 * bytes of the body it claimed, also when that body hides its available bytes in a wrapper.
 */
class UnstartedDiscardTest {
    private static final ByteBodyFactory FACTORY = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);

    private static CloseableByteBody withTrailers(CountingReadBuffer buffer) {
        SimpleHttpHeaders trailers = new SimpleHttpHeaders();
        trailers.add("x-trailer", "value");
        return FACTORY.withTrailers(FACTORY.adapt(buffer), CompletableFuture.completedFuture(trailers));
    }

    @Test
    void closingAnUnstartedLimitedBodyReleasesTheBuffer() {
        CountingReadBuffer buffer = new CountingReadBuffer("foo");
        CloseableByteBody limited = FACTORY.limit(withTrailers(buffer), 100);

        limited.close();

        assertEquals(1, buffer.closes.get(), "the limited body leaked its buffer");
    }

    @Test
    void discardingAnUnstartedLimitedBodyReleasesTheBuffer() {
        CountingReadBuffer buffer = new CountingReadBuffer("foo");
        BaseStreamingByteBody<?> limited = (BaseStreamingByteBody<?>) FACTORY.limit(withTrailers(buffer), 100);

        // a writer claims the body and discards it before it starts, e.g. when the connection
        // closed before the request was sent
        limited.primary(new BufferConsumer() {
            @Override
            public void add(ReadBuffer rb) {
                rb.close();
            }

            @Override
            public void complete() {
                // not needed for this test
            }

            @Override
            public void error(Throwable e) {
                // not needed for this test
            }
        }).allowDiscard();

        assertEquals(1, buffer.closes.get(), "the limited body leaked its buffer");
    }

    @Test
    void discardingAnUnstartedReplayableReaderReleasesTheBuffer() {
        CountingReadBuffer buffer = new CountingReadBuffer("foo");
        ReplayableByteBody replayable = FACTORY.replayable(withTrailers(buffer), 100);

        // a writer claims a reader and discards it before it starts, then the body is dropped
        BaseStreamingByteBody<?> reader = (BaseStreamingByteBody<?>) replayable.next();
        reader.primary(new BufferConsumer() {
            @Override
            public void add(ReadBuffer rb) {
                rb.close();
            }

            @Override
            public void complete() {
                // not needed for this test
            }

            @Override
            public void error(Throwable e) {
                // not needed for this test
            }
        }).allowDiscard();
        replayable.close();

        assertEquals(1, buffer.closes.get(), "the replayable body leaked its buffer");
    }

    @Test
    void closingAnUnstartedReplayableBodyReleasesTheBuffer() {
        CountingReadBuffer buffer = new CountingReadBuffer("foo");
        ReplayableByteBody replayable = FACTORY.replayable(withTrailers(buffer), 100);

        replayable.close();

        assertEquals(1, buffer.closes.get(), "the replayable body leaked its buffer");
    }
}
