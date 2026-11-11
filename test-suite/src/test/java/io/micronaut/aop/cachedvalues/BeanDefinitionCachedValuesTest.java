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
import io.micronaut.core.order.Ordered;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.annotation.builders.Sample;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The values kept by {@link BeanDefinition#getCachedValue(Class, java.util.function.Function)}.
 */
class BeanDefinitionCachedValuesTest {

    private ApplicationContext context;

    @BeforeEach
    void start() {
        context = ApplicationContext.run(Map.of(
            "spec.name", "CachedValuesTest",
            "cached.sample.name", "from-environment"
        ));
    }

    @AfterEach
    void stop() {
        context.close();
    }

    @Test
    void aValueReadFromTheMetadataOfAMethodIsComputedAgainWhenThePropertiesChange() {
        BeanDefinition<PlainBean> definition = definition();

        assertEquals("from-environment", definition.getCachedValue(String.class, BeanDefinitionCachedValuesTest::sampleName));
        assertEquals("from-environment", definition.getCachedValue(String.class, d -> "other"));

        context.getEnvironment().addPropertySource(PropertySource.of("override", Map.of("cached.sample.name", "changed"), Ordered.HIGHEST_PRECEDENCE));

        assertEquals("changed", definition.getCachedValue(String.class, BeanDefinitionCachedValuesTest::sampleName));
        assertEquals("changed", definition.getCachedValue(String.class, d -> "other"));
        assertEquals("changed", definition.getCachedUnevaluatedValue(String.class, BeanDefinitionCachedValuesTest::sampleName));
    }

    @Test
    void racingCallsGetTheSameKeptValue() throws Exception {
        BeanDefinition<PlainBean> definition = definition();
        int threads = 4;
        // Every factory waits for the others, so that they all compute before one is kept
        CyclicBarrier computing = new CyclicBarrier(threads);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Object>> results = new ArrayList<>(threads);
            for (int i = 0; i < threads; i++) {
                results.add(executor.submit(() -> definition.getCachedValue(Object.class, d -> {
                    try {
                        computing.await(10, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                    return new Object();
                })));
            }
            Object kept = results.get(0).get(10, TimeUnit.SECONDS);
            for (Future<Object> result : results) {
                assertSame(kept, result.get(10, TimeUnit.SECONDS));
            }
            assertSame(kept, definition.getCachedValue(Object.class, d -> new Object()));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void aValueComputedWhileThePropertiesChangeIsNotKept() throws Exception {
        BeanDefinition<PlainBean> definition = definition();
        CountDownLatch computing = new CountDownLatch(1);
        CountDownLatch changed = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> stale = executor.submit(() -> definition.getCachedValue(String.class, d -> {
                try {
                    return sampleName(d);
                } finally {
                    computing.countDown();
                    try {
                        assertTrue(changed.await(10, TimeUnit.SECONDS));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }));
            if (!computing.await(10, TimeUnit.SECONDS)) {
                stale.get(1, TimeUnit.SECONDS); // surfaces why the factory didn't run
                fail("The factory did not run");
            }
            context.getEnvironment().addPropertySource(PropertySource.of("override", Map.of("cached.sample.name", "changed"), Ordered.HIGHEST_PRECEDENCE));
            changed.countDown();

            assertEquals("from-environment", stale.get(10, TimeUnit.SECONDS), "the call that computed it still gets its value");
            assertEquals("changed", definition.getCachedValue(String.class, BeanDefinitionCachedValuesTest::sampleName));
        } finally {
            executor.shutdownNow();
        }
    }

    private static String sampleName(BeanDefinition<PlainBean> definition) {
        return definition.getRequiredMethod("placeholderAnnotated").stringValue(Sample.class, "name").orElse(null);
    }

    private BeanDefinition<PlainBean> definition() {
        // Creating the bean configures its definition for this context
        context.getBean(PlainBean.class);
        return context.getBeanDefinition(PlainBean.class);
    }
}
