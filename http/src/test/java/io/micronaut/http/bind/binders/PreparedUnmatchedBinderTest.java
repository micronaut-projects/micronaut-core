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
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PreparedUnmatchedBinderTest {
    @Test
    void preparationKeepsBinderOrderAndLegacyPostponedDispatch() {
        List<String> calls = new ArrayList<>();
        AtomicInteger prepared = new AtomicInteger();
        RequestArgumentBinder<Object> before = new RequestArgumentBinder<>() {
            @Override
            public RequestArgumentBinder<Object> createSpecific(Argument<Object> arg) {
                prepared.incrementAndGet();
                return this;
            }
            @Override
            public BindingResult<Object> bind(ArgumentConversionContext<Object> arg, HttpRequest<?> request) {
                calls.add("before");
                return BindingResult.unsatisfied();
            }
        };
        PostponedRequestArgumentBinder<Object> after = new PostponedRequestArgumentBinder<>() {
            @Override
            public BindingResult<Object> bind(ArgumentConversionContext<Object> arg, HttpRequest<?> request) {
                calls.add("after");
                return BindingResult.converted("value");
            }
            @Override
            public BindingResult<Object> bindPostponed(ArgumentConversionContext<Object> arg, HttpRequest<?> request) {
                fail("The unmatched binder historically calls bind on postponed children");
                return BindingResult.unsatisfied();
            }
        };
        var binder = new DefaultUnmatchedRequestArgumentBinder<Object>(List.of(before), List.of(), List.of(after));
        var specific = (PostponedRequestArgumentBinder<Object>) binder.createSpecific(Argument.OBJECT_ARGUMENT);
        var context = ConversionContext.of(Argument.OBJECT_ARGUMENT);
        for (int i = 0; i < 2; i++) {
            assertFalse(specific.bind(context, HttpRequest.GET("/")).isSatisfied());
            assertEquals("value", specific.bindPostponed(context, HttpRequest.GET("/")).get());
        }
        assertEquals(List.of("before", "after", "before", "after"), calls);
        assertEquals(1, prepared.get());
    }

    @Test
    void genericBindersObserveRegistrationsButSpecificBindersKeepTheirSnapshot() {
        List<RequestArgumentBinder<Object>> registrations = new ArrayList<>();
        var generic = new DefaultUnmatchedRequestArgumentBinder<Object>(List.of(), registrations, List.of());
        var specific = generic.createSpecific(Argument.OBJECT_ARGUMENT);
        registrations.add((context, request) -> () -> Optional.of("late"));
        var context = ConversionContext.of(Argument.OBJECT_ARGUMENT);
        assertEquals("late", generic.bind(context, HttpRequest.GET("/")).get());
        assertFalse(specific.bind(context, HttpRequest.GET("/")).isSatisfied());
    }

    @Test
    void emptyResultsLeaveTheArgumentUnfulfilledWithoutPendingWork() {
        var generic = new DefaultUnmatchedRequestArgumentBinder<Object>(
            List.of((context, request) -> BindingResult.empty()), List.of(), List.of());
        for (var binder : List.of(generic, generic.createSpecific(Argument.OBJECT_ARGUMENT))) {
            var result = binder.bind(ConversionContext.of(Argument.OBJECT_ARGUMENT), HttpRequest.GET("/"));
            assertSame(BindingResult.UNSATISFIED, result);
            assertFalse(result.isPending());
            assertFalse(result.isSatisfied());
            assertTrue(result.getValue().isEmpty());
        }
    }
}
