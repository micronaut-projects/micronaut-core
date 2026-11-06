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
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link BaseSharedBuffer#addAndComplete(ReadBuffer)} copies the final bytes for each streaming
 * subscriber before completing the buffer. If completing fails (a caller with an expected length
 * that was not reached) or a delivery throws, the copies not yet handed over must be closed, not
 * dropped.
 */
class AddAndCompleteFailureTest {

    @Test
    @Timeout(10)
    void copiesAreClosedWhenCompletionFailsOnTheExpectedLength() {
        ByteBodyFactory bbf = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
        Sinks.Many<ReadBuffer> source = Sinks.many().unicast().onBackpressureBuffer();
        // fewer bytes than announced: completing throws IncorrectContentLengthException
        try (CloseableByteBody body = bbf.adapt(source.asFlux(), OptionalLong.of(100))) {
            BaseSharedBuffer sharedBuffer = ((BaseStreamingByteBody<?>) body).sharedBuffer;
            List<Throwable> errors = new ArrayList<>();
            // two streaming subscribers, so two copies are made
            Flux.from(body.split(ByteBody.SplitBackpressureMode.FASTEST).toReadBufferPublisher()).doOnNext(ReadBuffer::close).doOnError(errors::add).subscribe();
            Flux.from(body.toReadBufferPublisher()).doOnNext(ReadBuffer::close).doOnError(errors::add).subscribe();

            AtomicInteger closedCopies = new AtomicInteger();
            ReadBuffer last = new TrackingReadBuffer(bbf.readBufferFactory().copyOf("]", StandardCharsets.UTF_8), closedCopies);

            assertThrows(BaseSharedBuffer.IncorrectContentLengthException.class, () -> sharedBuffer.addAndComplete(last));

            // both copies were created for the subscribers and neither was delivered
            assertEquals(2, closedCopies.get());
        }
    }

    /**
     * Counts the closes of the duplicates it hands out.
     */
    private static final class TrackingReadBuffer extends ReadBuffer {
        private final ReadBuffer delegate;
        private final AtomicInteger closedCopies;

        TrackingReadBuffer(ReadBuffer delegate, AtomicInteger closedCopies) {
            this.delegate = delegate;
            this.closedCopies = closedCopies;
        }

        @Override
        public int readable() {
            return delegate.readable();
        }

        @Override
        public ReadBuffer duplicate() {
            ReadBuffer copy = delegate.duplicate();
            return new ReadBuffer() {
                @Override
                public int readable() {
                    return copy.readable();
                }

                @Override
                public ReadBuffer duplicate() {
                    return copy.duplicate();
                }

                @Override
                public ReadBuffer split(int splitPosition) {
                    return copy.split(splitPosition);
                }

                @Override
                public ReadBuffer move() {
                    return copy.move();
                }

                @Override
                public void toArray(byte[] destination, int offset) {
                    copy.toArray(destination, offset);
                }

                @Override
                public void close() {
                    closedCopies.incrementAndGet();
                    copy.close();
                }

                @Override
                protected boolean isConsumed() {
                    return false;
                }

                @Override
                protected byte[] peekArray(int n) {
                    return copy.duplicate().toArray();
                }
            };
        }

        @Override
        public ReadBuffer split(int splitPosition) {
            return delegate.split(splitPosition);
        }

        @Override
        public ReadBuffer move() {
            return delegate.move();
        }

        @Override
        public void toArray(byte[] destination, int offset) {
            delegate.toArray(destination, offset);
        }

        @Override
        public void close() {
            delegate.close();
        }

        @Override
        protected boolean isConsumed() {
            return false;
        }

        @Override
        protected byte[] peekArray(int n) {
            return delegate.duplicate().toArray();
        }
    }
}
