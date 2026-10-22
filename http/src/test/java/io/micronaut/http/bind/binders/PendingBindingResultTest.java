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
package io.micronaut.http.bind.binders;

import io.micronaut.core.bind.ArgumentBinder.BindingResult;
import io.micronaut.core.convert.ConversionError;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PendingBindingResultTest {
    @Test
    void mappingWaitsForBothLevelsAndDoesNotCacheEarlyAbsence() {
        var source = new PendingValue();
        var child = new PendingValue();
        AtomicInteger calls = new AtomicInteger();
        var result = source.flatMap(v -> { calls.incrementAndGet(); return child; })
            .flatMap(v -> BindingResult.converted(v + "!"));
        assertInstanceOf(PendingRequestBindingResult.class, result);
        assertTrue(result.getValue().isEmpty());
        assertTrue(result.getConversionErrors().isEmpty());
        assertFalse(result.isSatisfied());
        assertEquals(0, calls.get());
        source.ready = true;
        assertTrue(result.isPending());
        assertFalse(result.isSatisfied());
        assertTrue(result.getValue().isEmpty());
        child.ready = true;
        assertFalse(result.isPending());
        assertTrue(result.isSatisfied());
        assertTrue(result.isConvertedToArgumentType());
        assertEquals("ready!", result.get());
        assertEquals(1, calls.get());
    }

    @Test
    void lateErrorsAndEmptyValuesArePreserved() {
        var source = new PendingValue();
        ConversionError error = () -> new IllegalArgumentException("invalid");
        var result = source.flatMap(v -> new BindingResult<String>() {
            @Override
            public Optional<String> getValue() { return Optional.empty(); }
            @Override
            public List<ConversionError> getConversionErrors() { return List.of(error); }
        });
        source.ready = true;
        assertFalse(result.isPending());
        assertFalse(result.isSatisfied());
        assertEquals(List.of(error), result.getConversionErrors());
        assertTrue(result.getValue().isEmpty());
        var empty = source.flatMap(v -> BindingResult.empty());
        assertFalse(empty.isPending());
        assertTrue(empty.isSatisfied());
        assertTrue(empty.getValue().isEmpty());
    }

    @Test
    void coreAndHttpCompositionKeepAbsenceErrorsAndRetryBehavior() {
        ConversionError error = () -> new IllegalArgumentException("bad value");
        for (boolean http : List.of(false, true)) {
            for (String state : List.of("empty", "unsatisfied", "error", "value")) {
                BindingResult<String> source = http ? new PendingRequestBindingResult<>() {
                    @Override
                    public boolean isPending() { return false; }
                    @Override
                    public boolean isSatisfied() { return !state.equals("unsatisfied") && !state.equals("error"); }
                    @Override
                    public Optional<String> getValue() { return state.equals("value") ? Optional.of("ready") : Optional.empty(); }
                    @Override
                    public List<ConversionError> getConversionErrors() { return state.equals("error") ? List.of(error) : List.of(); }
                } : new BindingResult<>() {
                    @Override
                    public boolean isSatisfied() { return !state.equals("unsatisfied") && !state.equals("error"); }
                    @Override
                    public Optional<String> getValue() { return state.equals("value") ? Optional.of("ready") : Optional.empty(); }
                    @Override
                    public List<ConversionError> getConversionErrors() { return state.equals("error") ? List.of(error) : List.of(); }
                };
                AtomicInteger calls = new AtomicInteger();
                var mapped = source.flatMap(value -> {
                    if (calls.incrementAndGet() == 1) {
                        throw new IllegalStateException("retry");
                    }
                    return BindingResult.converted(Optional.of(List.of(value)));
                });
                if (state.equals("value")) {
                    assertThrows(IllegalStateException.class, mapped::getValue);
                    var value = mapped.getValue();
                    assertSame(value, mapped.getValue());
                    assertEquals(Optional.of(Optional.of(List.of("ready"))), value);
                    assertTrue(mapped.isConvertedToArgumentType());
                    assertEquals(2, calls.get());
                } else {
                    assertTrue(mapped.getValue().isEmpty());
                    assertEquals(!state.equals("unsatisfied") && !state.equals("error"), mapped.isSatisfied());
                    assertEquals(state.equals("error") ? List.of(error) : List.of(), mapped.getConversionErrors());
                    assertEquals(0, calls.get());
                }
                assertEquals(http, mapped instanceof PendingRequestBindingResult<?>);
            }
        }
    }

    static final class PendingValue implements PendingRequestBindingResult<String> {
        boolean ready;
        @Override
        public boolean isPending() { return !ready; }
        @Override
        public Optional<String> getValue() { return ready ? Optional.of("ready") : Optional.empty(); }
    }
}
