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
package io.micronaut.json.bind;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.bind.BeanPropertyBinder;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Nested indexed form keys must be bounded in depth and in aggregate array padding.
 */
class NestedFormKeyLimitsTest {

    private static String key(int segments) {
        return "a[99].".repeat(segments) + "x";
    }

    @Test
    void deeplyNestedKeyDoesNotOverflowTheStack() {
        try (ApplicationContext ctx = ApplicationContext.run()) {
            BeanPropertyBinder binder = ctx.getBean(BeanPropertyBinder.class);
            try {
                binder.bind(Map.class, Map.<CharSequence, Object>of(key(6000), "1").entrySet());
            } catch (StackOverflowError e) {
                fail("StackOverflowError escaped the binder for a deeply nested key");
            } catch (Exception expected) {
                // a conversion error is the correct answer
            }
        }
    }

    @Test
    void nestedIndexedKeyDoesNotAmplifyAllocation() {
        try (ApplicationContext ctx = ApplicationContext.run()) {
            BeanPropertyBinder binder = ctx.getBean(BeanPropertyBinder.class);
            com.sun.management.ThreadMXBean mx = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
            String k = key(400);
            try {
                binder.bind(Map.class, Map.<CharSequence, Object>of(key(10), "1").entrySet()); // warm up
            } catch (Exception ignored) {
            }
            long before = mx.getCurrentThreadAllocatedBytes();
            boolean rejected = false;
            try {
                binder.bind(Map.class, Map.<CharSequence, Object>of(k, "1").entrySet());
            } catch (Exception e) {
                rejected = true;
            }
            long allocated = mx.getCurrentThreadAllocatedBytes() - before;
            assertTrue(rejected || allocated < 100L * k.length(),
                "a " + k.length() + " byte key allocated " + allocated + " bytes and was accepted");
        }
    }
}
