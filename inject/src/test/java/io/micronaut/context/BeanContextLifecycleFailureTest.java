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
package io.micronaut.context;

import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.ShutdownEvent;
import io.micronaut.context.event.StartupEvent;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeanContextLifecycleFailureTest {

    @Test
    void aShutdownListenerFailureDoesNotPreventDisposalOrRestart() {
        AtomicInteger disposed = new AtomicInteger();
        IllegalStateException failure = new IllegalStateException("shutdown listener failed");
        try (DefaultBeanContext context = new DefaultBeanContext()) {
            context.registerBeanDefinition(RuntimeBeanDefinition.builder(Resource.class, Resource::new)
                .singleton(true).disposer((creation, bean) -> disposed.incrementAndGet()).build());
            context.registerSingleton(failingShutdownListener(failure));
            context.start();
            context.getBean(Resource.class);

            assertSame(failure, assertThrows(IllegalStateException.class, context::stop));
            assertEquals(1, disposed.get());
            assertFalse(context.isRunning());
            assertTrue(context.isStoppingOrStopped());
            context.stop();
            assertEquals(1, disposed.get());

            context.start();
            assertTrue(context.isRunning());
        }
    }

    @Test
    void cleanupFailuresDoNotReplaceTheOriginalShutdownFailure() {
        IllegalStateException failure = new IllegalStateException("shutdown listener failed");
        AssertionError cleanup = new AssertionError("disposal failed");
        AtomicInteger attempted = new AtomicInteger();
        try (DefaultBeanContext context = new DefaultBeanContext()) {
            context.registerSingleton(failingShutdownListener(failure));
            context.registerBeanDefinition(RuntimeBeanDefinition.builder(Resource.class, Resource::new).singleton(true)
                .disposer((creation, bean) -> {
                    attempted.incrementAndGet();
                    throw cleanup;
                }).build());
            context.registerBeanDefinition(RuntimeBeanDefinition.builder(OtherResource.class, OtherResource::new).singleton(true)
                .disposer((creation, bean) -> attempted.incrementAndGet()).build());
            context.start();
            context.getBean(Resource.class);
            context.getBean(OtherResource.class);

            assertSame(failure, assertThrows(IllegalStateException.class, context::stop));
            assertEquals(2, attempted.get());
            assertTrue(Arrays.stream(failure.getSuppressed()).anyMatch(suppressed -> suppressed == cleanup || suppressed.getCause() == cleanup));
            assertFalse(context.isRunning());
        }
    }

    @Test
    void aDestructionErrorStillAttemptsEverySingletonAndResetsTheContext() {
        AssertionError failure = new AssertionError("disposal failed");
        AtomicInteger disposed = new AtomicInteger();
        try (DefaultBeanContext context = new DefaultBeanContext()) {
            context.registerBeanDefinition(RuntimeBeanDefinition.builder(Resource.class, Resource::new).singleton(true)
                .disposer((creation, bean) -> {
                    disposed.incrementAndGet();
                    throw failure;
                }).build());
            context.registerBeanDefinition(RuntimeBeanDefinition.builder(OtherResource.class, OtherResource::new).singleton(true)
                .disposer((creation, bean) -> disposed.incrementAndGet()).build());
            context.start();
            context.getBean(Resource.class);
            context.getBean(OtherResource.class);

            assertSame(failure, assertThrows(AssertionError.class, context::stop));
            assertEquals(2, disposed.get());
            assertFalse(context.isRunning());
            context.stop();
        }
    }

    @Test
    void aFailedStartCannotSilentlySucceedOnTheNextCall() {
        AtomicInteger starts = new AtomicInteger();
        AtomicInteger disposed = new AtomicInteger();
        IllegalStateException failure = new IllegalStateException("startup failed");
        try (DefaultBeanContext context = new DefaultBeanContext()) {
            context.registerBeanDefinition(RuntimeBeanDefinition.builder(Resource.class, Resource::new).singleton(true)
                .disposer((creation, bean) -> disposed.incrementAndGet()).build());
            context.registerSingleton(new ApplicationEventListener<StartupEvent>() {
                @Override
                public void onApplicationEvent(StartupEvent event) {
                    starts.incrementAndGet();
                    context.getBean(Resource.class);
                    throw failure;
                }
            });

            assertSame(failure, assertThrows(IllegalStateException.class, context::start));
            assertFalse(context.isRunning());
            assertThrows(IllegalStateException.class, context::start);
            assertEquals(1, starts.get());

            context.stop();
            assertEquals(0, disposed.get());
            context.stopFailedStart(List.of());
            assertEquals(1, disposed.get());
            assertFalse(context.isRunning());
            context.start();
            assertTrue(context.isRunning());
        }
    }

    @Test
    void aReentrantStartDoesNotFinishTheOuterStartupPrematurely() {
        AtomicInteger starts = new AtomicInteger();
        try (DefaultBeanContext context = new DefaultBeanContext()) {
            context.registerSingleton(new ApplicationEventListener<StartupEvent>() {
                @Override
                public void onApplicationEvent(StartupEvent event) {
                    starts.incrementAndGet();
                    context.start();
                    assertFalse(context.isRunning());
                    assertFalse(context.isStoppingOrStopped());
                }
            });

            context.start();

            assertEquals(1, starts.get());
            assertTrue(context.isRunning());
        }
    }

    @Test
    void anApplicationContextStopsItsEnvironmentEvenWhenShutdownFails() {
        IllegalStateException failure = new IllegalStateException("shutdown listener failed");
        try (ApplicationContext context = ApplicationContext.run()) {
            context.registerSingleton(failingShutdownListener(failure));

            assertSame(failure, assertThrows(IllegalStateException.class, context::stop));

            assertFalse(context.isRunning());
            assertFalse(context.getEnvironment().isRunning());
        }
    }

    @Test
    void aFailedRetainingShutdownDisposesResourcesItCannotReturn() {
        AtomicInteger disposed = new AtomicInteger();
        IllegalStateException failure = new IllegalStateException("shutdown failed");
        try (DefaultBeanContext context = new DefaultBeanContext()) {
            context.registerBeanDefinition(RuntimeBeanDefinition.builder(Resource.class, Resource::new).singleton(true)
                .disposer((creation, bean) -> disposed.incrementAndGet()).build());
            context.registerSingleton(failingShutdownListener(failure));
            context.start();
            context.getBean(Resource.class);

            assertSame(failure, assertThrows(IllegalStateException.class,
                () -> context.stopRetaining(registration -> registration.bean instanceof Resource)));
            assertEquals(1, disposed.get());
            assertFalse(context.isRunning());
        }
    }

    @Test
    void aThrowingRetentionPredicateStillDisposesEverySingleton() {
        AtomicInteger disposed = new AtomicInteger();
        IllegalStateException failure = new IllegalStateException("retention failed");
        try (DefaultBeanContext context = new DefaultBeanContext()) {
            context.registerBeanDefinition(RuntimeBeanDefinition.builder(Resource.class, Resource::new).singleton(true)
                .disposer((creation, bean) -> disposed.incrementAndGet()).build());
            context.registerBeanDefinition(RuntimeBeanDefinition.builder(OtherResource.class, OtherResource::new).singleton(true)
                .disposer((creation, bean) -> disposed.incrementAndGet()).build());
            context.start();
            context.getBean(Resource.class);
            context.getBean(OtherResource.class);

            assertSame(failure, assertThrows(IllegalStateException.class,
                () -> context.stopRetaining(registration -> { throw failure; })));
            assertEquals(2, disposed.get());
            assertFalse(context.isRunning());
        }
    }

    @Test
    void aLateShutdownTracerFailureDoesNotOrphanRetainedResources() {
        AtomicInteger disposed = new AtomicInteger();
        IllegalStateException failure = new IllegalStateException("shutdown tracing failed");
        BeanResolutionTracer tracer = new BeanResolutionTracer() {
            @Override
            public void traceContextShutdown(BeanContext context) {
                throw failure;
            }
        };
        try (DefaultBeanContext context = new DefaultBeanContext(new BeanContextConfiguration() {
            @Override
            public List<BeanResolutionTracer> getBeanResolutionTracers() {
                return List.of(tracer);
            }
        })) {
            context.registerBeanDefinition(RuntimeBeanDefinition.builder(Resource.class, Resource::new).singleton(true)
                .disposer((creation, bean) -> disposed.incrementAndGet()).build());
            context.start();
            context.getBean(Resource.class);

            assertSame(failure, assertThrows(IllegalStateException.class,
                () -> context.stopRetaining(registration -> registration.bean instanceof Resource)));
            assertEquals(1, disposed.get());
            assertFalse(context.isRunning());
        }
    }

    @Test
    void aShutdownDependentErrorDoesNotAbandonEarlierDependents() {
        AtomicInteger disposed = new AtomicInteger();
        AssertionError failure = new AssertionError("dependent disposal failed");
        try (DefaultBeanContext context = new DefaultBeanContext()) {
            context.registerBeanDefinition(RuntimeBeanDefinition.builder(Resource.class, Resource::new)
                .disposer((creation, bean) -> disposed.incrementAndGet()).build());
            context.registerBeanDefinition(RuntimeBeanDefinition.builder(OtherResource.class, OtherResource::new)
                .disposer((creation, bean) -> { disposed.incrementAndGet(); throw failure; }).build());
            context.registerSingleton(new ApplicationEventListener<ShutdownEvent>() {
                @Override
                public void onApplicationEvent(ShutdownEvent event) {
                    BeanDependencyGroup group = context.createDependencyGroup();
                    group.getBean(Resource.class);
                    group.getBean(OtherResource.class);
                }
            });
            context.start();

            assertSame(failure, assertThrows(AssertionError.class, context::stop));
            assertEquals(2, disposed.get());
            assertFalse(context.isRunning());
        }
    }

    private static ApplicationEventListener<ShutdownEvent> failingShutdownListener(RuntimeException failure) {
        return new ApplicationEventListener<>() {
            @Override
            public void onApplicationEvent(ShutdownEvent event) {
                throw failure;
            }
        };
    }

    static final class Resource {
    }

    static final class OtherResource {
    }
}
