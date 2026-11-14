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

import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanType;
import io.micronaut.inject.QualifiedBeanType;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeanContextLookupCacheTest {

    @Test
    void constructorExclusionsDoNotEscapeIntoPublicCollections() {
        try (DefaultBeanContext context = (DefaultBeanContext) new DefaultBeanContext().start()) {
            context.registerBeanDefinition(RuntimeBeanDefinition.builder(Leaf.class, Leaf::new).singleton(true).build());
            context.registerBeanDefinition(RuntimeBeanDefinition.builder(Composite.class,
                (RuntimeBeanDefinition.CreationContext creation) -> new Composite(creation.getBeansOfType(Argument.of(Service.class))))
                .singleton(true).build());

            Composite composite = context.getBean(Composite.class);

            assertEquals(1, composite.delegates.size());
            assertEquals(2, context.getBeansOfType(Service.class).size());
            assertTrue(context.getBeansOfType(Service.class).contains(composite));
        }
    }

    @Test
    void aWarmCollectionStillAppliesConstructorExclusions() {
        try (DefaultBeanContext context = (DefaultBeanContext) new DefaultBeanContext().start()) {
            var composite = RuntimeBeanDefinition.builder(Composite.class, () -> new Composite(List.of())).singleton(true).build();
            context.registerBeanDefinition(composite);
            context.registerBeanDefinition(RuntimeBeanDefinition.builder(Leaf.class, Leaf::new).singleton(true).build());
            assertEquals(2, context.getBeansOfType(Service.class).size());

            Argument<List<Service>> argument = Argument.listOf(Service.class);
            try (var resolution = new DefaultBeanResolutionContext(context, composite);
                 var ignored = resolution.getPath().pushConstructorResolve(composite, "<init>", argument, new Argument<?>[]{argument})) {
                assertEquals(1, context.getBeanRegistrations(resolution, Argument.of(Service.class), null).size());
            }
            assertEquals(2, context.getBeansOfType(Service.class).size());
        }
    }

    @Test
    void callersCannotModifyTheCachedRegistrations() {
        try (DefaultBeanContext context = (DefaultBeanContext) new DefaultBeanContext().start()) {
            context.registerBeanDefinition(RuntimeBeanDefinition.builder(Leaf.class, Leaf::new).singleton(true).build());
            Collection<BeanRegistration<Service>> registrations = context.getBeanRegistrations(Service.class);

            assertThrows(UnsupportedOperationException.class, registrations::clear);
            assertEquals(1, context.getBeansOfType(Service.class).size());
        }
    }

    @Test
    void existingInstanceCandidatesStaySeparateFromAssignableCandidates() {
        try (DefaultBeanContext context = (DefaultBeanContext) new DefaultBeanContext().start()) {
            context.registerBeanDefinition(RuntimeBeanDefinition.builder(Parent.class, Parent::new).build());
            context.registerBeanDefinition(RuntimeBeanDefinition.builder(Child.class, Child::new).build());

            context.inject(new Parent());
            assertEquals(2, context.getBeanDefinitions(Parent.class).size());
            assertEquals(1, context.findBeanCandidatesForInstance(new Parent()).size());
            assertEquals(2, context.getBeanDefinitions(Parent.class).size());
        }
    }

    @Test
    void aPresenceLookupRacingRegistrationDoesNotKeepItsOldAnswer() throws Exception {
        try (DefaultBeanContext context = (DefaultBeanContext) new DefaultBeanContext().start();
             var executor = Executors.newSingleThreadExecutor()) {
            context.registerBeanDefinition(RuntimeBeanDefinition.builder(Service.class, Leaf::new).singleton(true).named("first").build());
            BlockingQualifier qualifier = new BlockingQualifier(false);
            var pending = executor.submit(() -> context.containsBean(Service.class, qualifier));
            try {
                assertTrue(qualifier.computed.await(10, TimeUnit.SECONDS));
                context.registerBeanDefinition(RuntimeBeanDefinition.builder(Service.class, Leaf::new).singleton(true).named("wanted").build());
            } finally {
                qualifier.release.countDown();
            }

            assertFalse(pending.get(10, TimeUnit.SECONDS));
            assertTrue(context.containsBean(Service.class, qualifier));
        }
    }

    @Test
    void aCollectionLookupRacingRegistrationDoesNotKeepItsOldAnswer() throws Exception {
        try (DefaultBeanContext context = (DefaultBeanContext) new DefaultBeanContext().start();
             var executor = Executors.newSingleThreadExecutor()) {
            context.registerBeanDefinition(RuntimeBeanDefinition.builder(Service.class, Leaf::new).singleton(true).named("first").build());
            BlockingQualifier qualifier = new BlockingQualifier(true);
            var pending = executor.submit(() -> context.getBeansOfType(Service.class, qualifier));
            try {
                assertTrue(qualifier.computed.await(10, TimeUnit.SECONDS));
                context.registerBeanDefinition(RuntimeBeanDefinition.builder(Service.class, Leaf::new).singleton(true).named("wanted").build());
            } finally {
                qualifier.release.countDown();
            }

            assertEquals(1, pending.get(10, TimeUnit.SECONDS).size());
            assertEquals(2, context.getBeansOfType(Service.class, qualifier).size());
        }
    }

    @Test
    void registeringTheLookupTypeInvalidatesCachedAliasMisses() {
        BeanResolutionCustomizer customizer = new BeanResolutionCustomizer() {
            @Override
            public Argument<?> resolveBeanLookupArgument(Argument<?> type) {
                return type.getType() == int.class ? Argument.of(Integer.class) : type;
            }
        };
        try (ApplicationContext context = ApplicationContext.builder().beanResolutionCustomizer(customizer).start()) {
            assertFalse(context.containsBean(int.class));
            assertTrue(context.findBean(int.class).isEmpty());

            context.registerBeanDefinition(RuntimeBeanDefinition.builder(Integer.class, () -> 42).singleton(true).build());

            assertTrue(context.containsBean(int.class));
            assertEquals(42, context.getBean(int.class));
        }
    }

    @Test
    void destroyingAnInstanceInvalidatesCachedAliasCollections() {
        BeanResolutionCustomizer customizer = new BeanResolutionCustomizer() {
            @Override
            public Argument<?> resolveBeanLookupArgument(Argument<?> type) {
                return type.getType() == int.class ? Argument.of(Integer.class) : type;
            }
        };
        try (ApplicationContext context = ApplicationContext.builder().beanResolutionCustomizer(customizer).start()) {
            AtomicInteger created = new AtomicInteger();
            context.registerBeanDefinition(RuntimeBeanDefinition.builder(Integer.class, created::incrementAndGet)
                .singleton(true).named("value").build());
            assertEquals(List.of(1), context.getBeansOfType(Argument.INT, Qualifiers.byName("value")));

            context.destroyBean(Argument.of(Integer.class), Qualifiers.byName("value"));

            assertEquals(List.of(2), context.getBeansOfType(Argument.INT, Qualifiers.byName("value")));
        }
    }

    private static final class BlockingQualifier implements Qualifier<Service> {
        final CountDownLatch computed = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        private final AtomicBoolean first = new AtomicBoolean(true);
        private final boolean includeAll;

        BlockingQualifier(boolean includeAll) {
            this.includeAll = includeAll;
        }

        @Override
        public <BT extends BeanType<Service>> Stream<BT> reduce(Class<Service> type, Stream<BT> candidates) {
            List<BT> result = candidates.filter(candidate -> includeAll
                || candidate instanceof QualifiedBeanType<?> qualified && Qualifiers.byName("wanted").equals(qualified.getDeclaredQualifier())).toList();
            if (first.compareAndSet(true, false)) {
                computed.countDown();
                try {
                    assertTrue(release.await(10, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
            }
            return result.stream();
        }

        @Override
        public boolean equals(Object other) {
            return this == other;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }
    }

    interface Service {
    }

    static final class Leaf implements Service {
    }

    static final class Composite implements Service {
        final Collection<Service> delegates;

        Composite(Collection<Service> delegates) {
            this.delegates = delegates;
        }
    }

    static class Parent {
    }

    static final class Child extends Parent {
    }
}
