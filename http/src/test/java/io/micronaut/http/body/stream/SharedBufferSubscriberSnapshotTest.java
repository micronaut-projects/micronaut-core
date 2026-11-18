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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SharedBufferSubscriberSnapshotTest {
    private static final BufferConsumer.Upstream UPSTREAM = bytesConsumed -> { };

    @Test
    void terminalFluxDoesNotScheduleSubscriberRemoval() {
        for (boolean fail : new boolean[]{false, true}) {
            int[] removals = {0};
            BaseSharedBuffer buffer = new BaseSharedBuffer(ReadBufferFactory.getJdkFactory(), BodySizeLimits.UNLIMITED, UPSTREAM) {
                @Override
                protected void removeSubscriber(BufferConsumer subscriber) {
                    removals[0]++;
                }
            };
            var reader = new BaseSharedBuffer.AsFlux(buffer);
            var result = reader.asFlux(UPSTREAM).then().toFuture();
            buffer.subscribe0(reader, UPSTREAM);
            if (fail) {
                buffer.error(new IOException("test"));
                assertThrows(CompletionException.class, result::join);
            } else {
                buffer.complete();
                result.join();
            }
            assertEquals(0, removals[0], "terminal subscriber removal must not be scheduled");
        }
    }

    @Test
    void defaultRemovalAssertsThatTheSubclassSerializesMutations() {
        BaseSharedBuffer buffer = new BaseSharedBuffer(ReadBufferFactory.getJdkFactory(), BodySizeLimits.UNLIMITED, UPSTREAM) { };
        Reader reader = new Reader();
        buffer.subscribe0(reader, UPSTREAM);
        assertThrows(AssertionError.class, () -> buffer.removeSubscriber(reader));
        synchronized (buffer) {
            buffer.removeSubscriber(reader);
        }
        buffer.complete();
    }

    @Test
    void completionCanSubscribeAnotherReservedReader() {
        BaseSharedBuffer buffer = new BaseSharedBuffer(ReadBufferFactory.getJdkFactory(), BodySizeLimits.UNLIMITED, UPSTREAM) { };
        List<String> completed = new ArrayList<>();
        BufferConsumer late = new Reader() {
            @Override
            public void complete() {
                completed.add("late");
            }
        };
        buffer.reserve0();
        buffer.subscribe0(new Reader() {
            @Override
            public void complete() {
                completed.add("first");
                buffer.subscribe0(late, UPSTREAM);
            }
        }, UPSTREAM);

        buffer.complete();

        assertEquals(List.of("first", "late"), completed);
    }

    @Test
    void failureCanSubscribeAnotherReservedReader() {
        BaseSharedBuffer buffer = new BaseSharedBuffer(ReadBufferFactory.getJdkFactory(), BodySizeLimits.UNLIMITED, UPSTREAM) { };
        List<Throwable> errors = new ArrayList<>();
        BufferConsumer late = new Reader() {
            @Override
            public void error(Throwable e) {
                errors.add(e);
            }
        };
        buffer.reserve0();
        buffer.subscribe0(new Reader() {
            @Override
            public void error(Throwable e) {
                errors.add(e);
                buffer.subscribe0(late, UPSTREAM);
            }
        }, UPSTREAM);
        IOException failure = new IOException("test");

        buffer.error(failure);

        assertEquals(2, errors.size());
        assertSame(failure, errors.get(0));
        assertSame(failure, errors.get(1));
    }

    private static class Reader implements BufferConsumer {
        @Override
        public void add(ReadBuffer buf) {
            buf.close();
        }

        @Override
        public void complete() {
        }

        @Override
        public void error(Throwable e) {
        }
    }
}
