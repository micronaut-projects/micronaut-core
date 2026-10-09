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
package io.micronaut.http.client;

import io.micronaut.http.body.BodyElements;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MappedBodyElementsTest {
    @Test
    void nextRetainsMappingFailureWithoutReadingAnotherElement() {
        AtomicInteger reads = new AtomicInteger();
        AtomicInteger closed = new AtomicInteger();
        var source = BodyElements.of(() -> CompletableFuture.completedFuture(
            Optional.of(reads.getAndIncrement() == 0 ? "bad" : "42")), closed::incrementAndGet);
        var elements = MappedBodyElements.map(source, Integer::parseInt);
        Throwable failure = assertThrows(CompletionException.class, () -> elements.next().toCompletableFuture().join()).getCause();
        assertEquals(BodyElements.State.FAILED, elements.state());
        assertSame(failure, elements.failure());
        assertSame(failure, assertThrows(CompletionException.class, () -> elements.next().toCompletableFuture().join()).getCause());
        assertEquals(null, elements.poll());
        assertSame(failure, assertThrows(CompletionException.class, () ->
            elements.forEach(value -> CompletableFuture.completedFuture(null)).toCompletableFuture().join()).getCause());
        assertEquals(1, reads.get());
        assertEquals(1, closed.get());
        assertThrows(IllegalStateException.class, elements::next);
    }

    @Test
    void pollRetainsMappingFailure() {
        AtomicInteger reads = new AtomicInteger();
        BodyElements<String> source = new BodyElements<>() {
            @Override
            public String poll() {
                return reads.getAndIncrement() == 0 ? "bad" : "42";
            }

            @Override
            public CompletionStage<Optional<String>> next() {
                throw new AssertionError("An element was read after the mapping failed");
            }
        };
        var elements = MappedBodyElements.map(source, Integer::parseInt);
        Throwable failure = assertThrows(NumberFormatException.class, elements::poll);
        assertEquals(BodyElements.State.FAILED, elements.state());
        assertSame(failure, elements.failure());
        assertEquals(null, elements.poll());
        assertSame(failure, assertThrows(CompletionException.class, () -> elements.next().toCompletableFuture().join()).getCause());
        assertEquals(1, reads.get());
    }

    @Test
    void forEachRetainsMappingFailureAndClosesTheSource() {
        AtomicInteger closed = new AtomicInteger();
        var source = BodyElements.of(() -> CompletableFuture.completedFuture(Optional.of("bad")), closed::incrementAndGet);
        var elements = MappedBodyElements.map(source, Integer::parseInt);
        Throwable failure = assertThrows(CompletionException.class, () ->
            elements.forEach(value -> CompletableFuture.completedFuture(null)).toCompletableFuture().join()).getCause();
        assertSame(failure, elements.failure());
        assertEquals(BodyElements.State.FAILED, elements.state());
        assertEquals(1, closed.get());
        assertThrows(IllegalStateException.class, elements::next);
    }
}
