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
package io.micronaut.core.bind;

import io.micronaut.core.bind.ArgumentBinder.BindingResult;
import io.micronaut.core.convert.ConversionError;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BindingResultTest {
    @Test
    void convertedValuesAreStableAndIncludeTheCompleteOptional() {
        var value = List.of(1, 2);
        var result = BindingResult.converted(value);
        assertSame(value, result.get());
        assertSame(result.getValue(), result.getValue());
        assertTrue(result.isConvertedToArgumentType());
        assertEquals(Optional.of(Optional.empty()), BindingResult.converted(Optional.empty()).getValue());
        assertThrows(NullPointerException.class, () -> BindingResult.converted(null));
    }

    @Test
    void mappingPreservesTheTargetConversionContract() {
        var converted = BindingResult.converted("source");
        assertTrue(converted.flatMap(v -> BindingResult.converted(List.of(v))).isConvertedToArgumentType());
        assertFalse(converted.flatMap(v -> () -> Optional.of(List.of(v))).isConvertedToArgumentType());
        assertFalse(BindingResult.empty().flatMap(v -> converted).isConvertedToArgumentType());
    }

    @Test
    void earlyReadsDoNotCacheAnIncompleteSourceAndMappingRunsOnce() {
        var source = new PendingValue();
        AtomicInteger mapped = new AtomicInteger();
        var result = source.flatMap(v -> {
            mapped.incrementAndGet();
            return BindingResult.converted(v + "!");
        });
        assertTrue(result.isPending());
        assertFalse(result.isSatisfied());
        assertTrue(result.getValue().isEmpty());
        assertTrue(result.getConversionErrors().isEmpty());
        assertFalse(result.isConvertedToArgumentType());
        assertEquals(0, mapped.get());
        source.pending = false;
        assertFalse(result.isPending());
        assertTrue(result.isSatisfied());
        assertEquals("ready!", result.get());
        assertEquals("ready!", result.get());
        assertTrue(result.isConvertedToArgumentType());
        assertEquals(1, mapped.get());
    }

    @Test
    void anImmediateSourceCanMapToPendingWork() {
        var child = new PendingValue();
        var result = BindingResult.converted("source").flatMap(v -> child).flatMap(v -> BindingResult.converted(v + "!"));
        assertTrue(result.isPending());
        assertTrue(result.getValue().isEmpty());
        child.pending = false;
        assertFalse(result.isPending());
        assertEquals("ready!", result.get());
    }

    @Test
    void unsatisfiedAndFailedSourcesNeverRunTheMapping() {
        AtomicInteger mapped = new AtomicInteger();
        var unsatisfied = BindingResult.unsatisfied().flatMap(v -> {
            mapped.incrementAndGet();
            return BindingResult.converted(v);
        });
        ConversionError error = () -> new IllegalArgumentException("bad input");
        BindingResult<String> failed = new BindingResult<>() {
            @Override
        public Optional<String> getValue() { return Optional.of("ignored"); }
            @Override
        public List<ConversionError> getConversionErrors() { return List.of(error); }
        };
        var result = failed.flatMap(v -> {
            mapped.incrementAndGet();
            return BindingResult.converted(v);
        });
        assertFalse(unsatisfied.isSatisfied());
        assertTrue(unsatisfied.getValue().isEmpty());
        assertEquals(List.of(error), result.getConversionErrors());
        assertFalse(result.isSatisfied());
        assertTrue(result.getValue().isEmpty());
        assertEquals(0, mapped.get());
    }

    static final class PendingValue implements BindingResult<String> {
        boolean pending = true;
        @Override
        public boolean isPending() { return pending; }
        @Override
        public Optional<String> getValue() { return pending ? Optional.empty() : Optional.of("ready"); }
    }
}
