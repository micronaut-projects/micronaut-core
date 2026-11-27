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
package io.micronaut.docs.aop.retry;

import io.micronaut.context.ApplicationContext;
import io.micronaut.retry.BulkheadRegistry;
import io.micronaut.retry.exception.BulkheadFullException;
import io.micronaut.retry.exception.TimeLimitExceededException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BulkheadTimeLimitTest {

    @Test
    void testBulkheadRefusesACallBeyondItsLimit() throws Exception {
        try (ApplicationContext context = ApplicationContext.run()) {
            ReportService service = context.getBean(ReportService.class);

            CompletableFuture<String> first = service.render("first");
            service.render("second");
            CompletableFuture<String> third = service.render("third");

            ExecutionException e = assertThrows(ExecutionException.class, () -> third.get(5, TimeUnit.SECONDS));
            assertInstanceOf(BulkheadFullException.class, e.getCause());
            assertFalse(service.isStarted("third"));

            // a call that ends gives its place to the next
            service.finish("first");
            assertEquals("first done", first.get(5, TimeUnit.SECONDS));
            service.render("fourth");
            assertTrue(service.isStarted("fourth"));
        }
    }

    @Test
    void testMethodsOfOneNameShareThePlacesAndWaitForOne() throws Exception {
        try (ApplicationContext context = ApplicationContext.run()) {
            ReportService service = context.getBean(ReportService.class);
            BulkheadRegistry registry = context.getBean(BulkheadRegistry.class);

            CompletableFuture<String> stored = service.store("stored");
            CompletableFuture<String> loaded = service.load("loaded");

            // the second waits for the one place of the bulkhead
            assertFalse(service.isStarted("loaded"));
            assertEquals(1, registry.findSnapshot("archive").orElseThrow().active());
            assertEquals(1, registry.findSnapshot("archive").orElseThrow().waiting());

            service.finish("stored");
            assertEquals("stored done", stored.get(5, TimeUnit.SECONDS));
            assertTrue(service.isStarted("loaded"));
            service.finish("loaded");
            assertEquals("loaded done", loaded.get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void testTimeLimitFailsACallThatTakesTooLong() throws Exception {
        try (ApplicationContext context = ApplicationContext.run()) {
            ReportService service = context.getBean(ReportService.class);

            CompletableFuture<String> late = service.export("late");
            ExecutionException e = assertThrows(ExecutionException.class, () -> late.get(5, TimeUnit.SECONDS));
            assertInstanceOf(TimeLimitExceededException.class, e.getCause());

            CompletableFuture<String> inTime = service.export("in time");
            service.finish("in time");
            assertEquals("in time done", inTime.get(5, TimeUnit.SECONDS));
        }
    }
}
