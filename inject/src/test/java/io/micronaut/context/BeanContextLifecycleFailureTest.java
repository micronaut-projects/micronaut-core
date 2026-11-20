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
import io.micronaut.context.event.BeanDestroyedEventListener;
import io.micronaut.context.event.ShutdownEvent;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
            public void traceBeanCreation(Resolution resolution, BeanDefinition<?> definition, Argument<?> beanType) {
            }

            @Override
            public void traceBeanCreated(Resolution resolution, BeanDefinition<?> definition) {
            }

            @Override
            public void traceInjectBean(Resolution resolution, Injection injection) {
            }

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

    @Test
    void aShutdownDependentRuntimeFailureIsLoggedAsASingletonDestructionFailureIs() {
        AtomicInteger disposed = new AtomicInteger();
        try (DefaultBeanContext context = new DefaultBeanContext()) {
            context.registerBeanDefinition(RuntimeBeanDefinition.builder(Resource.class, Resource::new)
                .disposer((creation, bean) -> { disposed.incrementAndGet(); throw new IllegalStateException("dependent disposal failed"); }).build());
            context.registerBeanDefinition(RuntimeBeanDefinition.builder(OtherResource.class, OtherResource::new)
                .disposer((creation, bean) -> disposed.incrementAndGet()).build());
            context.registerSingleton(dependentsCreatedOnShutdown(context));
            context.start();

            assertDoesNotThrow(context::stop);
            assertEquals(2, disposed.get());
            assertFalse(context.isRunning());
        }
    }

    @Test
    void aShutdownDependentRuntimeFailureDoesNotDiscardTheRetainedBeans() {
        AtomicInteger disposed = new AtomicInteger();
        try (DefaultBeanContext context = new DefaultBeanContext()) {
            context.registerBeanDefinition(RuntimeBeanDefinition.builder(Retained.class, Retained::new).singleton(true)
                .disposer((creation, bean) -> disposed.incrementAndGet()).build());
            context.registerBeanDefinition(RuntimeBeanDefinition.builder(Resource.class, Resource::new)
                .disposer((creation, bean) -> { throw new IllegalStateException("dependent disposal failed"); }).build());
            context.registerBeanDefinition(RuntimeBeanDefinition.builder(OtherResource.class, OtherResource::new).build());
            context.registerSingleton(dependentsCreatedOnShutdown(context));
            context.start();
            Retained retained = context.getBean(Retained.class);

            Collection<BeanRegistration<?>> registrations = context.stopRetaining(registration -> registration.bean instanceof Retained);

            assertEquals(1, registrations.size());
            assertSame(retained, registrations.iterator().next().bean);
            assertEquals(0, disposed.get());
            registrations.forEach(context::destroyBean);
            assertEquals(1, disposed.get());
        }
    }

    @Test
    void aFailedStopOfAFailedStartStillHandsBackTheSurvivors() {
        AtomicInteger disposed = new AtomicInteger();
        AtomicInteger created = new AtomicInteger();
        IllegalStateException tracing = new IllegalStateException("shutdown tracing failed");
        RuntimeBeanDefinition<Retained> definition = RuntimeBeanDefinition.builder(Retained.class, () -> {
                created.incrementAndGet();
                return new Retained();
            }).singleton(true)
            .disposer((creation, bean) -> disposed.incrementAndGet()).build();
        Collection<BeanRegistration<?>> retained;
        try (DefaultBeanContext first = new DefaultBeanContext()) {
            first.registerBeanDefinition(definition);
            first.start();
            first.getBean(Retained.class);
            retained = first.stopRetaining(registration -> registration.bean instanceof Retained);
        }
        assertEquals(1, retained.size());

        DefaultBeanContext failed = new DefaultBeanContext(new BeanContextConfiguration() {
            @Override
            public List<BeanResolutionTracer> getBeanResolutionTracers() {
                return List.of(failingShutdownTracer(tracing));
            }

            @Override
            public Collection<BeanRegistration<?>> getRetainedRegistrations() {
                return retained;
            }
        });
        failed.registerSingleton(failingStartupListener(new IllegalStateException("startup failed")));
        assertThrows(IllegalStateException.class, failed::start);

        Collection<BeanRegistration<?>> handedBack = assertDoesNotThrow(() -> failed.stopFailedStart(retained));

        assertEquals(List.copyOf(retained), List.copyOf(handedBack));
        assertEquals(0, disposed.get());
        assertFalse(failed.isRunning());
        assertTrue(failed.stopFailedStart(retained).isEmpty());
        handedBack.forEach(failed::destroyBean);
        assertEquals(1, disposed.get());
        assertEquals(1, created.get());
    }

    @Test
    void retainedBeansAFailedStopCannotReturnAreDestroyedWithTheirListeners() {
        List<Object> destroyed = new CopyOnWriteArrayList<>();
        IllegalStateException failure = new IllegalStateException("shutdown tracing failed");
        try (DefaultBeanContext context = new DefaultBeanContext(new BeanContextConfiguration() {
            @Override
            public List<BeanResolutionTracer> getBeanResolutionTracers() {
                return List.of(failingShutdownTracer(failure));
            }
        })) {
            BeanDestroyedEventListener<Resource> listener = event -> destroyed.add(event.getBean());
            context.registerBeanDefinition(RuntimeBeanDefinition.builder(
                    Argument.of(BeanDestroyedEventListener.class, Resource.class), () -> listener)
                .singleton(true).build());
            context.registerBeanDefinition(RuntimeBeanDefinition.builder(Resource.class, Resource::new).singleton(true).build());
            context.start();
            Resource resource = context.getBean(Resource.class);

            assertSame(failure, assertThrows(IllegalStateException.class,
                () -> context.stopRetaining(registration -> registration.bean instanceof Resource)));
            assertEquals(List.of(resource), destroyed);
        }
    }

    @Test
    void aRetryOfAFailedApplicationContextStartDoesNotStartTheEnvironmentAgain() {
        ApplicationContext context = ApplicationContext.builder().build();
        try {
            context.registerSingleton(failingStartupListener(new IllegalStateException("startup failed")));
            assertThrows(IllegalStateException.class, context::start);
            context.getEnvironment().stop();

            assertThrows(IllegalStateException.class, context::start);

            assertFalse(context.getEnvironment().isRunning());
            ((DefaultBeanContext) context).stopFailedStart(List.of());
            context.start();
            assertTrue(context.isRunning());
        } finally {
            context.close();
        }
    }

    @Test
    void aRetentionDecisionThatFailedOnceIsMadeAgainWithoutDuplicates() {
        AtomicInteger asked = new AtomicInteger();
        try (DefaultBeanContext context = new DefaultBeanContext()) {
            context.registerBeanDefinition(RuntimeBeanDefinition.builder(Retained.class, Retained::new).singleton(true).build());
            context.registerBeanDefinition(RuntimeBeanDefinition.builder(OtherResource.class, OtherResource::new).singleton(true).build());
            context.registerSingleton(new ApplicationEventListener<ShutdownEvent>() {
                @Override
                public void onApplicationEvent(ShutdownEvent event) {
                    assertThrows(IllegalStateException.class, () -> context.isRetainedOnStop(this));
                }
            });
            context.start();
            Retained retained = context.getBean(Retained.class);
            context.getBean(OtherResource.class);

            Collection<BeanRegistration<?>> registrations = context.stopRetaining(registration -> {
                if (asked.getAndIncrement() == 0) {
                    throw new IllegalStateException("retention failed once");
                }
                return registration.bean instanceof Retained;
            });

            assertEquals(1, registrations.size());
            assertSame(retained, registrations.iterator().next().bean);
        }
    }

    private static ApplicationEventListener<ShutdownEvent> dependentsCreatedOnShutdown(DefaultBeanContext context) {
        return new ApplicationEventListener<>() {
            @Override
            public void onApplicationEvent(ShutdownEvent event) {
                BeanDependencyGroup group = context.createDependencyGroup();
                group.getBean(Resource.class);
                group.getBean(OtherResource.class);
            }
        };
    }

    private static ApplicationEventListener<StartupEvent> failingStartupListener(RuntimeException failure) {
        return new ApplicationEventListener<>() {
            @Override
            public void onApplicationEvent(StartupEvent event) {
                throw failure;
            }
        };
    }

    private static BeanResolutionTracer failingShutdownTracer(RuntimeException failure) {
        return new BeanResolutionTracer() {
            @Override
            public void traceBeanCreation(Resolution resolution, BeanDefinition<?> definition, Argument<?> beanType) {
            }

            @Override
            public void traceBeanCreated(Resolution resolution, BeanDefinition<?> definition) {
            }

            @Override
            public void traceInjectBean(Resolution resolution, Injection injection) {
            }

            @Override
            public void traceContextShutdown(BeanContext context) {
                throw failure;
            }
        };
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

    static final class Retained {
    }
}
