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
import io.micronaut.inject.qualifiers.Qualifiers;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RetainedRuntimeSingletonAdoptionTest {

    @Test
    void retainedFactorySingletonIsAdoptedUnderTheEquivalentDefinitionOfTheNextContext() {
        AtomicInteger created = new AtomicInteger();
        AtomicInteger disposed = new AtomicInteger();
        Collection<BeanRegistration<?>> retained;
        Item item;
        try (DefaultBeanContext context = (DefaultBeanContext) BeanContext.run()) {
            context.registerBeanDefinition(itemDefinition(created, disposed));
            item = context.getBean(Item.class);
            retained = context.stopRetaining(registration -> registration.getBeanType() == Item.class);
        }
        assertEquals(1, created.get());
        assertEquals(0, disposed.get());

        try (ApplicationContext context = ApplicationContext.builder()
            .beanDefinitions(itemDefinition(created, disposed))
            .retainedRegistrations(retained)
            .start()) {
            assertSame(item, context.getBean(Item.class));
            assertEquals(1, context.getBeansOfType(Item.class).size());
            assertEquals(1, created.get());
        }
        assertEquals(1, disposed.get());
    }

    @Test
    void retainedAnyFactorySingletonsAreAdoptedUnderTheEquivalentDefinitionOfTheNextContext() {
        AtomicInteger created = new AtomicInteger();
        Collection<BeanRegistration<?>> retained;
        Item abc;
        try (DefaultBeanContext context = (DefaultBeanContext) BeanContext.run()) {
            context.registerBeanDefinition(anyItemDefinition(created));
            abc = context.getBean(Item.class, Qualifiers.byName("ABC"));
            retained = context.stopRetaining(registration -> registration.getBeanType() == Item.class);
        }

        try (ApplicationContext context = ApplicationContext.builder()
            .beanDefinitions(anyItemDefinition(created))
            .retainedRegistrations(retained)
            .start()) {
            assertSame(abc, context.getBean(Item.class, Qualifiers.byName("ABC")));
            assertEquals(1, created.get());
            Item xyz = context.getBean(Item.class, Qualifiers.byName("XYZ"));
            assertNotSame(abc, xyz);
            assertEquals(2, created.get());
        }
    }

    @Test
    void retainedFactorySingletonsAreNotAdoptedUnderAnAmbiguousDefinition() {
        AtomicInteger created = new AtomicInteger();
        AtomicInteger disposed = new AtomicInteger();
        Collection<BeanRegistration<?>> retained;
        try (DefaultBeanContext context = (DefaultBeanContext) BeanContext.run()) {
            context.registerBeanDefinition(itemDefinition(created, disposed));
            context.getBean(Item.class);
            retained = context.stopRetaining(registration -> registration.getBeanType() == Item.class);
        }

        try (ApplicationContext context = ApplicationContext.builder()
            .beanDefinitions(itemDefinition(created, disposed), itemDefinition(created, disposed))
            .retainedRegistrations(retained)
            .start()) {
            assertThrows(NonUniqueBeanException.class, () -> context.getBean(Item.class));
        }
    }

    @Test
    void lookupsOfRuntimeSingletonsFollowRegistrationsOfTheSameType() {
        try (BeanContext context = BeanContext.run()) {
            context.registerBeanDefinition(RuntimeBeanDefinition.builder(Item.class, () -> new Item("one")).singleton(true).named("same").build());
            Item one = context.getBean(Item.class, Qualifiers.byName("same"));
            assertSame(one, context.getBean(Item.class, Qualifiers.byName("same")));
            assertSame(one, context.getBean(Item.class));

            context.registerBeanDefinition(RuntimeBeanDefinition.builder(Item.class, () -> new Item("two")).singleton(true).named("same").build());

            assertThrows(NonUniqueBeanException.class, () -> context.getBean(Item.class, Qualifiers.byName("same")));
            assertThrows(NonUniqueBeanException.class, () -> context.getBean(Item.class));
        }
    }

    private static RuntimeBeanDefinition<Item> itemDefinition(AtomicInteger created, AtomicInteger disposed) {
        return RuntimeBeanDefinition.builder(Item.class, () -> new Item("item-" + created.incrementAndGet()))
            .singleton(true)
            .disposer((context, bean) -> disposed.incrementAndGet())
            .build();
    }

    private static RuntimeBeanDefinition<Item> anyItemDefinition(AtomicInteger created) {
        return RuntimeBeanDefinition.builder(Item.class, () -> new Item("any-" + created.incrementAndGet()))
            .singleton(true)
            .qualifier(Qualifiers.any())
            .build();
    }

    record Item(String value) {
    }
}
