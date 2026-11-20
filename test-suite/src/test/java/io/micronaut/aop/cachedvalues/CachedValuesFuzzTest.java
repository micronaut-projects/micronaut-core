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
package io.micronaut.aop.cachedvalues;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.PropertySource;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.order.Ordered;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.annotation.builders.Sample;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Random concurrent use of the values kept by methods while their properties change. Run more rounds with
 * {@code -Dcached.values.fuzz.rounds=<n>} and a given seed with {@code -Dcached.values.fuzz.seed=<seed>}.
 */
class CachedValuesFuzzTest {

    private static final int THREADS = 6;
    private static final int OPERATIONS = 3_000;

    record Version(int value) {
    }

    record Other(int value) {
    }

    @Test
    void keptValuesFollowThePropertiesUnderConcurrentUse() throws Exception {
        int rounds = Integer.getInteger("cached.values.fuzz.rounds", 3);
        long seed = Long.getLong("cached.values.fuzz.seed", System.nanoTime());
        for (int round = 0; round < rounds; round++) {
            long roundSeed = seed + round;
            try {
                runRound(roundSeed);
            } catch (Throwable e) {
                fail("Round failed with -Dcached.values.fuzz.seed=" + roundSeed, e);
            }
        }
    }

    private void runRound(long seed) throws Exception {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "spec.name", "CachedValuesTest",
            "cached.plan.value", "v0",
            "cached.sample.name", "v0"))) {
            context.getBean(PlainBean.class);
            PlannedBean planned = context.getBean(PlannedBean.class);
            ExecutableMethod<PlainBean, Object> placeholder = context.getBeanDefinition(PlainBean.class).getRequiredMethod("placeholderAnnotated");
            ExecutableMethod<PlainBean, Object> fixed = context.getBeanDefinition(PlainBean.class).getRequiredMethod("annotated");
            Object fixedValue = fixed.getCachedValue(Object.class, m -> new Object());

            AtomicInteger version = new AtomicInteger();
            AtomicBoolean writing = new AtomicBoolean(true);
            ExecutorService executor = Executors.newFixedThreadPool(THREADS + 1);
            try {
                Future<?> writer = executor.submit(() -> {
                    Random random = new Random(seed);
                    for (int i = 1; i <= 40; i++) {
                        sleepMicros(random.nextInt(2_000));
                        String value = "v" + i;
                        context.getEnvironment().addPropertySource(PropertySource.of(
                            "fuzz-" + i,
                            Map.of("cached.sample.name", value, "cached.plan.value", value),
                            Ordered.HIGHEST_PRECEDENCE + i));
                        version.set(i);
                    }
                    writing.set(false);
                    return null;
                });
                List<Future<?>> readers = new ArrayList<>();
                for (int t = 0; t < THREADS; t++) {
                    long threadSeed = seed * 31 + t;
                    readers.add(executor.submit(() -> {
                        Random random = new Random(threadSeed);
                        for (int i = 0; i < OPERATIONS || writing.get(); i++) {
                            int op = random.nextInt(6);
                            int done = version.get();
                            int seen = switch (op) {
                                case 0 -> placeholder.getCachedValue(Version.class, CachedValuesFuzzTest::version).value();
                                case 1 -> {
                                    AnnotationValue<Sample> value = placeholder.getCachedAnnotationValue(Sample.class);
                                    assertNotNull(value);
                                    yield versionOf(value.stringValue("name").orElseThrow());
                                }
                                case 2 -> {
                                    Sample sample = placeholder.getCachedAnnotation(Sample.class);
                                    assertNotNull(sample);
                                    yield versionOf(sample.name());
                                }
                                case 3 -> {
                                    // A value kept under the annotation type never replaces the annotation entries
                                    assertNull(placeholder.getCachedValue(Sample.class, m -> null));
                                    yield done;
                                }
                                case 4 -> versionOf(planned.placeholder());
                                default -> {
                                    assertSame(fixedValue, fixed.getCachedValue(Object.class, m -> new Object()));
                                    Other other = fixed.getCachedValue(Other.class, m -> new Other(1));
                                    assertEquals(1, other.value());
                                    yield done;
                                }
                            };
                            // A call never returns a version older than the last change completed before it
                            assertTrue(seen >= done, "operation " + op + " saw v" + seen + " after v" + done + " was added");
                        }
                        return null;
                    }));
                }
                writer.get(60, TimeUnit.SECONDS);
                for (Future<?> reader : readers) {
                    reader.get(60, TimeUnit.SECONDS);
                }
            } finally {
                executor.shutdownNow();
            }

            // Once the properties stop changing, every kept value is the current one
            int last = version.get();
            assertEquals(last, placeholder.getCachedValue(Version.class, CachedValuesFuzzTest::version).value());
            assertEquals(last, versionOf(placeholder.getCachedAnnotationValue(Sample.class).stringValue("name").orElseThrow()));
            assertEquals(last, versionOf(placeholder.getCachedAnnotation(Sample.class).name()));
            assertEquals(last, versionOf(planned.placeholder()));
            assertSame(fixedValue, fixed.getCachedValue(Object.class, m -> new Object()));
        }
    }

    private static Version version(ExecutableMethod<?, ?> method) {
        return new Version(versionOf(method.stringValue(Sample.class, "name").orElseThrow()));
    }

    private static int versionOf(String value) {
        return Integer.parseInt(value.substring(1));
    }

    private static void sleepMicros(int micros) {
        try {
            TimeUnit.MICROSECONDS.sleep(micros);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
