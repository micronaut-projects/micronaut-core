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

import io.micronaut.core.execution.DelayedExecutionFlow;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SharedBufferTerminalCallbackFailureTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void everyReaderIsTerminatedBeforeRethrowingTheFirstCallbackFailure(boolean failed) {
        BufferConsumer.Upstream upstream = bytes -> { };
        var buffer = new BaseSharedBuffer(ReadBufferFactory.getJdkFactory(), BodySizeLimits.UNLIMITED, upstream) { };
        var first = new IllegalStateException("first callback");
        var second = new AssertionError("second callback");
        var cause = new IllegalArgumentException("body failure");
        List<Integer> notified = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            buffer.reserve0();
            int index = i;
            buffer.subscribe0(new BufferConsumer() {
                @Override
                public void add(ReadBuffer rb) {
                    rb.close();
                }

                private void terminated() {
                    notified.add(index);
                    if (index == 0 || index == 2) {
                        throw first; // Reusing a failure must not cause self-suppression.
                    }
                    if (index == 1) {
                        throw second;
                    }
                }

                @Override
                public void complete() {
                    assertFalse(failed);
                    terminated();
                }

                @Override
                public void error(Throwable e) {
                    assertTrue(failed);
                    assertSame(cause, e);
                    terminated();
                }
            }, upstream);
        }
        DelayedExecutionFlow<ReadBuffer> full = DelayedExecutionFlow.create();
        buffer.subscribeFull0(full, upstream, false);

        assertSame(first, assertThrows(IllegalStateException.class, () -> {
            if (failed) {
                buffer.error(cause);
            } else {
                buffer.complete();
            }
        }));
        assertEquals(List.of(0, 1, 2, 3), notified);
        assertArrayEquals(new Throwable[]{second}, first.getSuppressed());
        if (failed) {
            assertSame(cause, full.tryCompleteError());
        } else {
            full.toCompletableFuture().join().close();
        }
        buffer.complete();
        buffer.error(cause);
        assertEquals(4, notified.size());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void everyFullReaderIsTerminatedBeforeRethrowingTheFirstCallbackFailure(boolean failed) {
        BufferConsumer.Upstream upstream = bytes -> { };
        var buffer = new BaseSharedBuffer(ReadBufferFactory.getJdkFactory(), BodySizeLimits.UNLIMITED, upstream) { };
        var first = new AssertionError("first full callback");
        var second = new AssertionError("second full callback");
        List<Integer> notified = new ArrayList<>();
        buffer.reserve0();
        buffer.reserve0();
        for (int i = 0; i < 3; i++) {
            int index = i;
            DelayedExecutionFlow<ReadBuffer> full = DelayedExecutionFlow.create();
            full.onComplete((data, error) -> {
                if (data != null) {
                    data.close();
                }
                notified.add(index);
                if (index == 0) {
                    throw first;
                }
                if (index == 1) {
                    throw second;
                }
            });
            buffer.subscribeFull0(full, upstream, false);
        }
        assertSame(first, assertThrows(AssertionError.class, () -> {
            if (failed) {
                buffer.error(new IllegalArgumentException("body failure"));
            } else {
                buffer.complete();
            }
        }));
        assertEquals(List.of(0, 1, 2), notified);
        assertArrayEquals(new Throwable[]{second}, first.getSuppressed());
    }
}
