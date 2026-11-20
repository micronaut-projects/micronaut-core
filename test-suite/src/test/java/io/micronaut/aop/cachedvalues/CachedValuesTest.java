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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class CachedValuesTest {

    private ApplicationContext context;

    @BeforeEach
    void start() {
        context = ApplicationContext.run(Map.of(
            "spec.name", "CachedValuesTest",
            "cached.plan.value", "first",
            "cached.sample.name", "from-environment"
        ));
    }

    @AfterEach
    void stop() {
        context.close();
    }

    @Test
    void aStaticPlanIsComputedOnce() {
        PlannedBean bean = context.getBean(PlannedBean.class);
        PlanInterceptor interceptor = context.getBean(PlanInterceptor.class);

        assertEquals("static", bean.fixed());
        assertEquals("static", bean.fixed());
        assertEquals("static", bean.fixed());
        assertEquals(1, interceptor.computed.get());
    }

    @Test
    void aPlanReadingAPropertyPlaceholderIsKeptUntilThePropertiesChange() {
        PlannedBean bean = context.getBean(PlannedBean.class);
        PlanInterceptor interceptor = context.getBean(PlanInterceptor.class);

        assertEquals("first", bean.placeholder());
        assertEquals("first", bean.placeholder());
        assertEquals(1, interceptor.computed.get());

        context.getEnvironment().addPropertySource(PropertySource.of("override", Map.of("cached.plan.value", "second"), Ordered.HIGHEST_PRECEDENCE));

        assertEquals("second", bean.placeholder());
        assertEquals("second", bean.placeholder());
        assertEquals(2, interceptor.computed.get());
    }

    @Test
    void aPlanReadingAnEvaluatedExpressionSeesTheArgumentsOfEachCall() {
        PlannedBean bean = context.getBean(PlannedBean.class);
        PlanInterceptor interceptor = context.getBean(PlanInterceptor.class);

        assertEquals("a!", bean.expression("a"));
        assertEquals("b!", bean.expression("b"));
        assertEquals("a!", bean.expression("a"));
        assertEquals(3, interceptor.computed.get());
    }

    @Test
    void annotationValuesAndBuiltAnnotationsAreKept() {
        ExecutableMethod<PlainBean, Object> method = method("annotated");

        AnnotationValue<Sample> value = method.getCachedAnnotationValue(Sample.class);
        assertNotNull(value);
        assertEquals("sample", value.stringValue("name").orElse(null));
        assertSame(value, method.getCachedAnnotationValue(Sample.class));

        Sample sample = method.getCachedAnnotation(Sample.class);
        assertNotNull(sample);
        assertEquals("sample", sample.name());
        assertEquals(5, sample.count());
        assertEquals("label", sample.label());
        assertFalse(Proxy.isProxyClass(sample.getClass()), "built by the annotation builder, not a reflective proxy");
        assertSame(sample, method.getCachedAnnotation(Sample.class));

        // The value and the annotation are kept apart under the same annotation type
        assertSame(value, method.getCachedAnnotationValue(Sample.class));
    }

    @Test
    void anAbsentAnnotationIsKeptAsAbsent() {
        ExecutableMethod<PlainBean, Object> method = method("annotated");

        assertNull(method.getCachedAnnotationValue(Planned.class));
        assertNull(method.getCachedAnnotationValue(Planned.class));
        assertNull(method.getCachedAnnotation(Planned.class));
    }

    @Test
    void anAnnotationWithoutABuilderIsNotSynthesized() {
        ExecutableMethod<PlannedBean, Object> method = context.getBeanDefinition(PlannedBean.class).getRequiredMethod("fixed");

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> method.getCachedAnnotation(Planned.class));
        assertEquals("No AnnotationBuilder is registered for " + Planned.class.getName() + ": register it with @RegisterAnnotations", e.getMessage());
    }

    @Test
    void anAnnotationReadingAPropertyPlaceholderIsKeptUntilThePropertiesChange() {
        ExecutableMethod<PlainBean, Object> method = method("placeholderAnnotated");

        Sample sample = method.getCachedAnnotation(Sample.class);
        assertNotNull(sample);
        assertEquals("from-environment", sample.name());
        assertSame(sample, method.getCachedAnnotation(Sample.class));

        context.getEnvironment().addPropertySource(PropertySource.of("override", Map.of("cached.sample.name", "changed"), Ordered.HIGHEST_PRECEDENCE));

        Sample changed = method.getCachedAnnotation(Sample.class);
        assertNotNull(changed);
        assertEquals("changed", changed.name());
    }

    @Test
    void valuesOfAMethodWithArgumentExpressionsAreNotKept() {
        ExecutableMethod<PlainBean, Object> method = method("argumentExpression", String.class);
        AtomicInteger computed = new AtomicInteger();

        method.getCachedValue(String.class, m -> "v" + computed.incrementAndGet());
        method.getCachedValue(String.class, m -> "v" + computed.incrementAndGet());

        assertEquals(2, computed.get());
    }

    @Test
    void valuesAreKeptPerKeyIncludingNull() {
        ExecutableMethod<PlainBean, Object> method = method("annotated");
        AtomicInteger computed = new AtomicInteger();

        assertEquals("text", method.getCachedValue(String.class, m -> {
            computed.incrementAndGet();
            return "text";
        }));
        assertEquals(42, method.getCachedValue(Integer.class, m -> {
            computed.incrementAndGet();
            return 42;
        }));
        assertNull(method.getCachedValue(Long.class, m -> {
            computed.incrementAndGet();
            return null;
        }));
        assertEquals("text", method.getCachedValue(String.class, m -> "other"));
        assertEquals(42, method.getCachedValue(Integer.class, m -> 0));
        assertNull(method.getCachedValue(Long.class, m -> 1L));
        assertEquals(3, computed.get());
    }

    @Test
    void racingCallsGetTheSameKeptValue() throws Exception {
        ExecutableMethod<PlainBean, Object> method = method("annotated");
        int threads = 4;
        // Every factory waits for the others, so that they all compute before one is kept
        CyclicBarrier computing = new CyclicBarrier(threads);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Object>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(executor.submit(() -> method.getCachedValue(Object.class, m -> {
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
            assertSame(kept, method.getCachedValue(Object.class, m -> new Object()));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void aBuiltAnnotationIsKeptApartFromAValueKeptUnderItsType() {
        ExecutableMethod<PlainBean, Object> method = method("annotated");

        assertNull(method.getCachedValue(Sample.class, m -> null));
        Sample sample = method.getCachedAnnotation(Sample.class);
        assertNotNull(sample);
        assertEquals("sample", sample.name());
        assertNull(method.getCachedValue(Sample.class, m -> sample));

        ExecutableMethod<PlainBean, Object> other = method("placeholderAnnotated");
        assertNotNull(other.getCachedAnnotation(Sample.class));
        AtomicInteger computed = new AtomicInteger();
        assertNull(other.getCachedValue(Sample.class, m -> {
            computed.incrementAndGet();
            return null;
        }));
        assertEquals(1, computed.get());
    }

    @Test
    void aConfigurationBeanGetterDoesNotReadThePropertyMembers() {
        PlannedConfig config = context.getBean(PlannedConfig.class);

        assertSame(context.getBean(PlainBean.class), config.getBean());
        assertSame(context.getBean(PlainBean.class), config.getBean());
        assertEquals("fallback", config.getName());
    }

    @Test
    void aValueComputedWhileThePropertiesChangeIsNotKept() throws Exception {
        ExecutableMethod<PlainBean, Object> method = method("placeholderAnnotated");
        CountDownLatch computing = new CountDownLatch(1);
        CountDownLatch changed = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> stale = executor.submit(() -> method.getCachedValue(String.class, m -> {
                try {
                    return m.stringValue(Sample.class, "name").orElse(null);
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
            assertEquals("changed", method.getCachedValue(String.class, m -> m.stringValue(Sample.class, "name").orElse(null)));
        } finally {
            executor.shutdownNow();
        }
    }

    private ExecutableMethod<PlainBean, Object> method(String name, Class<?>... argumentTypes) {
        // Creating the bean configures its definition for this context: the generated methods are shared by the contexts of the JVM
        context.getBean(PlainBean.class);
        return context.getBeanDefinition(PlainBean.class).getRequiredMethod(name, argumentTypes);
    }
}
