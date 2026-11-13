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

import io.micronaut.context.exceptions.NonUniqueBeanException;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.DisposableBeanDefinition;
import io.micronaut.inject.ProxyBeanDefinition;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BeanContextSingletonIdentityTest {

    @Test
    void exactDefinitionsRemainDistinctAndDoNotHideAmbiguity() {
        try (BeanContext context = BeanContext.run()) {
            AtomicInteger firstCalls = new AtomicInteger();
            AtomicInteger secondCalls = new AtomicInteger();
            var first = RuntimeBeanDefinition.builder(Item.class, () -> new Item("one-" + firstCalls.incrementAndGet())).singleton(true).build();
            var second = RuntimeBeanDefinition.builder(Item.class, () -> new Item("two-" + secondCalls.incrementAndGet())).singleton(true).build();
            context.registerBeanDefinition(first);
            context.registerBeanDefinition(second);

            Item one = context.getBean(first);
            assertThrows(NonUniqueBeanException.class, () -> context.getBean(Item.class));
            Item two = context.getBean(second);

            assertNotSame(one, two);
            assertSame(one, context.getBean(first));
            assertSame(two, context.getBean(second));
            assertEquals(1, firstCalls.get());
            assertEquals(1, secondCalls.get());
            assertEquals(Set.of("one-1", "two-1"), context.getBeansOfType(Item.class).stream().map(Item::value).collect(Collectors.toSet()));
            assertThrows(NonUniqueBeanException.class, () -> context.getBeanDefinition(Item.class));
        }
    }

    @Test
    void explicitSingletonRegistrationStillOverridesTypeLookups() {
        try (BeanContext context = BeanContext.run()) {
            context.registerBeanDefinition(RuntimeBeanDefinition.builder(Item.class, () -> new Item("factory one")).singleton(true).build());
            context.registerBeanDefinition(RuntimeBeanDefinition.builder(Item.class, () -> new Item("factory two")).singleton(true).build());
            Item provided = new Item("provided");

            context.registerSingleton(Item.class, provided, null, false);

            assertSame(provided, context.getBean(Item.class));
        }
    }

    @Test
    void qualifiedDelegatesKeepTheUnderlyingRuntimeDefinitionIdentity() {
        try (BeanContext context = BeanContext.run()) {
            var first = RuntimeBeanDefinition.builder(Item.class, () -> new Item("one")).singleton(true).qualifier(Qualifiers.any()).build();
            var second = RuntimeBeanDefinition.builder(Item.class, () -> new Item("two")).singleton(true).qualifier(Qualifiers.any()).build();
            context.registerBeanDefinition(first);
            context.registerBeanDefinition(second);
            var firstDelegate = BeanDefinitionDelegate.create(first, Qualifiers.byName("same"));
            var secondDelegate = BeanDefinitionDelegate.create(second, Qualifiers.byName("same"));

            Item one = context.getBean(firstDelegate);
            Item two = context.getBean(secondDelegate);

            assertEquals("one", one.value());
            assertEquals("two", two.value());
            assertNotSame(one, two);
            assertSame(one, context.getBean(firstDelegate));
            assertSame(two, context.getBean(secondDelegate));
        }
    }

    @Test
    void ignoringADependentSingletonProxyDoesNotClaimItsDestruction() {
        SharedDefinition definition = new SharedDefinition();
        try (BeanContext context = BeanContext.run()) {
            context.registerBeanDefinition(definition);
            BeanRegistration<Shared> registration = context.getBeanRegistration(Shared.class, null);

            context.destroyDependentBean(registration);
            assertEquals(0, definition.destroyed);

            context.destroyBean(registration);
            assertEquals(1, definition.destroyed);
        }
        assertEquals(1, definition.destroyed);
    }

    @Test
    void retainedRuntimeDefinitionsKeepTheirIdentityAndDestruction() {
        AtomicInteger disposed = new AtomicInteger();
        var first = RuntimeBeanDefinition.builder(Item.class, () -> new Item("one")).singleton(true)
            .disposer((context, bean) -> disposed.incrementAndGet()).build();
        var second = RuntimeBeanDefinition.builder(Item.class, () -> new Item("two")).singleton(true)
            .disposer((context, bean) -> disposed.incrementAndGet()).build();
        Collection<BeanRegistration<?>> retained;
        Item one;
        Item two;
        try (DefaultBeanContext context = (DefaultBeanContext) BeanContext.run()) {
            context.registerBeanDefinition(first);
            context.registerBeanDefinition(second);
            one = context.getBean(first);
            two = context.getBean(second);
            retained = context.stopRetaining(registration -> registration.getBeanType() == Item.class);
            assertEquals(0, disposed.get());
        }

        try (ApplicationContext context = ApplicationContext.builder().retainedRegistrations(retained).start()) {
            assertSame(one, context.getBean(first));
            assertSame(two, context.getBean(second));
            assertEquals(2, context.getBeansOfType(Item.class).size());
            assertThrows(NonUniqueBeanException.class, () -> context.getBean(Item.class));
        }
        assertEquals(2, disposed.get());
    }

    @Test
    void retainedExplicitSingletonsStillOverrideNewFactories() {
        Item provided = new Item("provided");
        Collection<BeanRegistration<?>> retained;
        try (DefaultBeanContext context = (DefaultBeanContext) BeanContext.run()) {
            context.registerSingleton(Item.class, provided, null, false);
            retained = context.stopRetaining(registration -> registration.getBeanType() == Item.class);
        }
        AtomicInteger created = new AtomicInteger();
        var factory = RuntimeBeanDefinition.builder(Item.class, () -> new Item("new-" + created.incrementAndGet())).singleton(true).build();

        try (ApplicationContext context = ApplicationContext.builder().beanDefinitions(factory).retainedRegistrations(retained).start()) {
            assertSame(provided, context.getBean(Item.class));
            assertEquals(0, created.get());
        }
    }

    @Test
    void retainedAnySingletonsKeepTheirRequestedQualifiers() {
        AtomicInteger created = new AtomicInteger();
        AtomicInteger disposed = new AtomicInteger();
        var factory = RuntimeBeanDefinition.builder(Item.class, () -> new Item("any-" + created.incrementAndGet()))
            .singleton(true).qualifier(Qualifiers.any()).disposer((context, bean) -> disposed.incrementAndGet()).build();
        Collection<BeanRegistration<?>> retained;
        Item abc;
        Item xyz;
        try (DefaultBeanContext context = (DefaultBeanContext) BeanContext.run()) {
            context.registerBeanDefinition(factory);
            abc = context.getBean(Item.class, Qualifiers.byName("ABC"));
            xyz = context.getBean(Item.class, Qualifiers.byName("XYZ"));
            assertNotSame(abc, xyz);
            retained = context.stopRetaining(registration -> registration.getBeanType() == Item.class);
        }
        assertEquals(0, disposed.get());

        try (ApplicationContext context = ApplicationContext.builder().retainedRegistrations(retained).start()) {
            assertSame(abc, context.getBean(Item.class, Qualifiers.byName("ABC")));
            assertSame(xyz, context.getBean(Item.class, Qualifiers.byName("XYZ")));
            assertEquals(2, created.get());
        }
        assertEquals(2, disposed.get());
    }

    @Test
    void retainedAnyDelegatesKeepTheExactRuntimeFactory() {
        var first = RuntimeBeanDefinition.builder(Item.class, () -> new Item("one")).singleton(true).qualifier(Qualifiers.any()).build();
        var second = RuntimeBeanDefinition.builder(Item.class, () -> new Item("two")).singleton(true).qualifier(Qualifiers.any()).build();
        var firstDelegate = BeanDefinitionDelegate.create(first, Qualifiers.byName("same"));
        var secondDelegate = BeanDefinitionDelegate.create(second, Qualifiers.byName("same"));
        Collection<BeanRegistration<?>> retained;
        Item one;
        Item two;
        try (DefaultBeanContext context = (DefaultBeanContext) BeanContext.run()) {
            context.registerBeanDefinition(first);
            context.registerBeanDefinition(second);
            one = context.getBean(firstDelegate);
            two = context.getBean(secondDelegate);
            retained = context.stopRetaining(registration -> registration.getBeanType() == Item.class);
        }

        try (ApplicationContext context = ApplicationContext.builder().retainedRegistrations(retained).start()) {
            assertSame(one, context.getBean(firstDelegate));
            assertSame(two, context.getBean(secondDelegate));
        }
    }

    record Item(String value) {
    }

    static final class Shared {
    }

    private static final class SharedDefinition implements RuntimeBeanDefinition<Shared>, ProxyBeanDefinition<Shared>, DisposableBeanDefinition<Shared> {
        private int destroyed;

        @Override
        public Class<Shared> getBeanType() {
            return Shared.class;
        }

        @Override
        public String getBeanDefinitionName() {
            return getClass().getName();
        }

        @Override
        public boolean isSingleton() {
            return true;
        }

        @Override
        public Shared instantiate(BeanResolutionContext resolutionContext, BeanContext context) {
            return new Shared();
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public Class<BeanDefinition<Shared>> getTargetDefinitionType() {
            return (Class) SharedDefinition.class;
        }

        @Override
        public Class<Shared> getTargetType() {
            return Shared.class;
        }

        @Override
        public Shared dispose(BeanResolutionContext resolutionContext, BeanContext context, Shared bean) {
            destroyed++;
            return bean;
        }
    }
}
