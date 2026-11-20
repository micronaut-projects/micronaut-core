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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The values kept by {@link BeanDefinition#getCachedUnevaluatedValue(Class, java.util.function.Function)}: the
 * argument of {@link PlainBean#argumentExpression(String)} has an evaluated expression, so
 * {@link BeanDefinition#getCachedValue(Class, java.util.function.Function)} keeps nothing for it.
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

        assertEquals("from-environment", definition.getCachedUnevaluatedValue(SampleName.class, BeanDefinitionCachedValuesTest::sampleName).value());
        assertEquals("from-environment", definition.getCachedUnevaluatedValue(SampleName.class, d -> new SampleName("other")).value());

        context.getEnvironment().addPropertySource(PropertySource.of("override", Map.of("cached.sample.name", "changed"), Ordered.HIGHEST_PRECEDENCE));

        assertEquals("changed", definition.getCachedUnevaluatedValue(SampleName.class, BeanDefinitionCachedValuesTest::sampleName).value());
        assertEquals("changed", definition.getCachedUnevaluatedValue(SampleName.class, d -> new SampleName("other")).value());
    }

    @Test
    void anEvaluatedExpressionOfAMemberKeepsNothing() {
        BeanDefinition<PlainBean> definition = definition();
        Token first = definition.getCachedValue(Token.class, d -> new Token());

        assertNotSame(first, definition.getCachedValue(Token.class, d -> new Token()));
    }

    @Test
    void racingCallsGetTheSameKeptValue() throws Exception {
        BeanDefinition<PlainBean> definition = definition();
        int threads = 4;
        // Every factory waits for the others, so that they all compute before one is kept
        CyclicBarrier computing = new CyclicBarrier(threads);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Token>> results = new ArrayList<>(threads);
            for (int i = 0; i < threads; i++) {
                results.add(executor.submit(() -> definition.getCachedUnevaluatedValue(Token.class, d -> {
                    try {
                        computing.await(10, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                    return new Token();
                })));
            }
            Token kept = results.get(0).get(10, TimeUnit.SECONDS);
            for (Future<Token> result : results) {
                assertSame(kept, result.get(10, TimeUnit.SECONDS));
            }
            assertSame(kept, definition.getCachedUnevaluatedValue(Token.class, d -> new Token()));
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
            Future<SampleName> stale = executor.submit(() -> definition.getCachedUnevaluatedValue(SampleName.class, d -> {
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

            assertEquals("from-environment", stale.get(10, TimeUnit.SECONDS).value(), "the call that computed it still gets its value");
            assertEquals("changed", definition.getCachedUnevaluatedValue(SampleName.class, BeanDefinitionCachedValuesTest::sampleName).value());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void aRefreshedEnvironmentComputesTheValuesAgain() {
        BeanDefinition<PlainBean> definition = definition();
        Token first = definition.getCachedUnevaluatedValue(Token.class, d -> new Token());
        assertSame(first, definition.getCachedUnevaluatedValue(Token.class, d -> new Token()));

        context.getEnvironment().refresh();

        Token second = definition.getCachedUnevaluatedValue(Token.class, d -> new Token());
        assertNotSame(first, second);
        assertSame(second, definition.getCachedUnevaluatedValue(Token.class, d -> new Token()));
    }

    private static SampleName sampleName(BeanDefinition<PlainBean> definition) {
        return new SampleName(definition.getRequiredMethod("placeholderAnnotated").stringValue(Sample.class, "name").orElse(null));
    }

    record SampleName(String value) {
    }

    static final class Token {
    }

    private BeanDefinition<PlainBean> definition() {
        // Creating the bean configures its definition for this context
        context.getBean(PlainBean.class);
        return context.getBeanDefinition(PlainBean.class);
    }
}
