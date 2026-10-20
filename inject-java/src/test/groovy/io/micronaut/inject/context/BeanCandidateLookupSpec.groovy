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
}
