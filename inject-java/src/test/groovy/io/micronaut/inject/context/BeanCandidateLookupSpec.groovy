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
package io.micronaut.inject.context

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.Qualifier
import io.micronaut.core.type.Argument
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanType
import io.micronaut.inject.qualifiers.Qualifiers

import java.util.stream.Stream

class BeanCandidateLookupSpec extends AbstractTypeElementSpec {

    private static final String BEANS = '''
package test;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Replaces;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.util.List;

public class Beans {

    @Prototype
    public static class Gadget {
    }

    public record Part(String name) {
    }

    @Singleton
    public static class Bolt {
        public final List<Part> parts;

        Bolt(List<Part> parts) {
            this.parts = parts;
        }
    }

    @Factory
    public static class PartLists {
        @Bean
        @Prototype
        List<Part> parts() {
            return List.of(new Part("bolt"));
        }

        @Prototype
        Part part() {
            return new Part("nut");
        }
    }

    public interface Engine {
    }

    @Singleton
    @Named("old")
    public static class OldEngine implements Engine {
    }

    @Singleton
    @Replaces(OldEngine.class)
    public static class NewEngine implements Engine {
    }

    public interface Pump {
    }

    @Singleton
    @Named("old")
    public static class OldPump implements Pump {
    }

    @EachProperty("pumps")
    @Replaces(OldPump.class)
    public static class ConfiguredPump implements Pump {
    }
}
'''

    void "destroying a prototype keeps the candidates of its types"() {
        given:
        ApplicationContext context = buildContext('test.Beans', BEANS)
        Class<?> gadget = type(context, 'Gadget')
        context.getBeanDefinitions(gadget)
        def field = DefaultBeanContext.getDeclaredField("beanCandidateCache")
        field.accessible = true
        Map<Argument<?>, ?> candidates = (Map<Argument<?>, ?>) field.get(context)

        expect:
        candidates.containsKey(Argument.of(gadget))

        when:
        3.times { context.destroyBean(context.getBean(gadget)) }

        then:
        candidates.containsKey(Argument.of(gadget))

        cleanup:
        context.close()
    }

    void "a raw lookup of a type a factory's list exposes finds it"() {
        given:
        ApplicationContext context = buildContext('test.Beans', BEANS)
        Class<?> part = type(context, 'Part')
        BeanDefinition<?> parts = context.getBeanDefinitions(List).find { definition ->
            definition.getTypeArguments(List)*.type == [part]
        }

        expect:
        parts.exposedTypes.contains(Collection)
        context.getBeanDefinitions(Collection).contains(parts)
        context.getBeanDefinitions(Argument.of(Collection)).contains(parts)
        context.getBeanDefinitions(Iterable).contains(parts)

        and: 'a list of the parts is still injected as all the part beans'
        context.getBean(type(context, 'Bolt')).parts*.name.sort() == ['bolt', 'nut']

        cleanup:
        context.close()
    }

    void "a replaced bean is left out before the qualifier whichever way the definitions are asked for"() {
        given:
        ApplicationContext context = buildContext('test.Beans', BEANS)
        Class<?> engine = type(context, 'Engine')
        Qualifier<Object> onlyTheOldEngine = new OnlyType(type(context, 'OldEngine'))

        expect:
        context.getBeanDefinitions((Class) engine, onlyTheOldEngine).isEmpty()
        context.getBeanDefinitions(onlyTheOldEngine).isEmpty()

        and: 'a filtering qualifier'
        context.getBeanDefinitions((Class) engine, Qualifiers.byName('old')).isEmpty()
        !context.getBeanDefinitions(Qualifiers.byName('old'))*.beanType.contains(type(context, 'OldEngine'))

        cleanup:
        context.close()
    }

    void "a qualifier that chooses among the definitions is given only those not replaced"() {
        given:
        ApplicationContext context = buildContext('test.Beans', BEANS)
        Class<?> engine = type(context, 'Engine')
        Qualifier<Object> preferTheOldEngine = new Prefer(type(context, 'OldEngine'), engine)

        expect: 'the replaced engine is not there to be preferred, so the other one is chosen'
        context.getBeanDefinitions((Class) engine, preferTheOldEngine)*.beanType == [type(context, 'NewEngine')]
        context.getBeanDefinitions(preferTheOldEngine)*.beanType == [type(context, 'NewEngine')]

        cleanup:
        context.close()
    }

    void "an each property bean that produces no bean replaces nothing whichever way the definitions are asked for"() {
        given:
        ApplicationContext context = buildContext('test.Beans', BEANS)
        Class<?> oldPump = type(context, 'OldPump')

        expect:
        context.getBeanDefinitions(type(context, 'Pump'))*.beanType == [oldPump]
        context.getBeanDefinitions(Qualifiers.byName('old'))*.beanType.contains(oldPump)
        context.getBeanDefinitions(new OnlyType(oldPump))*.beanType == [oldPump]

        cleanup:
        context.close()
    }

    private static Class<?> type(ApplicationContext context, String name) {
        context.classLoader.loadClass('test.Beans$' + name)
    }

    private static final class OnlyType implements Qualifier<Object> {
        private final Class<?> type

        OnlyType(Class<?> type) {
            this.type = type
        }

        @Override
        <B extends BeanType<Object>> Stream<B> reduce(Class<Object> beanType, Stream<B> candidates) {
            candidates.filter { it.beanType == type }
        }
    }

    /**
     * Chooses one definition of a type, the preferred one if it is there.
     */
    private static final class Prefer implements Qualifier<Object> {
        private final Class<?> preferred
        private final Class<?> type

        Prefer(Class<?> preferred, Class<?> type) {
            this.preferred = preferred
            this.type = type
        }

        @Override
        <B extends BeanType<Object>> Stream<B> reduce(Class<Object> beanType, Stream<B> candidates) {
            List<B> ofType = candidates.filter { type.isAssignableFrom(it.beanType) }.toList()
            B chosen = ofType.find { it.beanType == preferred } ?: ofType.find()
            chosen == null ? Stream.empty() : Stream.of(chosen)
        }
    }
}
