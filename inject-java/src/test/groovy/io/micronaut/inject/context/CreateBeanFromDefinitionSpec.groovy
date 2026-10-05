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
import io.micronaut.context.BeanContext
import io.micronaut.context.exceptions.NonUniqueBeanException
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.qualifiers.Qualifiers

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class CreateBeanFromDefinitionSpec extends AbstractTypeElementSpec {

    private static final String BEANS = '''
package test;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

public class Beans {

    @Singleton
    public static class Dependency {
    }

    @Prototype
    public static class Greeting {
        public final String name;
        public final int count;
        public final Dependency dependency;

        public Greeting(@Parameter String name, @Parameter int count, Dependency dependency) {
            this.name = name;
            this.count = count;
            this.dependency = dependency;
        }
    }

    @Prototype
    public static class Plain {
    }

    @Singleton
    public static class GreetingListener implements BeanCreatedEventListener<Greeting> {
        public int created;

        @Override
        public Greeting onCreated(BeanCreatedEvent<Greeting> event) {
            created++;
            return event.getBean();
        }
    }

    public static class Text {
        public final String value;

        Text(String value) {
            this.value = value;
        }
    }

    @Factory
    public static class TextFactory {
        @Prototype
        @Named("upper")
        Text upper(@Parameter String value) {
            return new Text(value.toUpperCase());
        }

        @Prototype
        @Named("lower")
        Text lower(@Parameter String value) {
            return new Text(value.toLowerCase());
        }
    }
}
'''

    void "creates a new instance of the definition with the parameter values"() {
        given:
        ApplicationContext context = buildContext('test.Beans', BEANS)
        BeanDefinition<?> definition = context.getBeanDefinition(type(context, 'Greeting'))

        when:
        def first = context.createBean(definition, 'a', 1)
        def second = context.createBean(definition, 'b', '2')

        then: 'every call creates a new instance from its own converted arguments and injects the dependencies'
        !first.is(second)
        first.name == 'a'
        first.count == 1
        second.name == 'b'
        second.count == 2
        first.dependency.is(context.getBean(type(context, 'Dependency')))
        second.dependency.is(first.dependency)

        and: 'the created bean listeners are notified as when the bean is created by type'
        context.getBean(type(context, 'GreetingListener')).created == 2

        cleanup:
        context.close()
    }

    void "creates a new instance of a definition without parameters"() {
        given:
        ApplicationContext context = buildContext('test.Beans', BEANS)
        BeanDefinition<?> definition = context.getBeanDefinition(type(context, 'Plain'))

        when:
        def first = context.createBean(definition)
        def second = context.createBean(definition)

        then:
        type(context, 'Plain').isInstance(first)
        !first.is(second)

        cleanup:
        context.close()
    }

    void "creates the instance of the given definition where the type alone is ambiguous"() {
        given:
        ApplicationContext context = buildContext('test.Beans', BEANS)
        Class<?> textType = type(context, 'Text')
        BeanDefinition<?> upper = context.getBeanDefinition(textType, Qualifiers.byName('upper'))
        BeanDefinition<?> lower = context.getBeanDefinition(textType, Qualifiers.byName('lower'))

        when:
        context.createBean(textType, 'MiXeD')

        then:
        thrown(NonUniqueBeanException)

        when:
        def upperText = context.createBean(upper, 'MiXeD')
        def lowerText = context.createBean(lower, 'MiXeD')

        then:
        upperText.value == 'MIXED'
        lowerText.value == 'mixed'

        cleanup:
        context.close()
    }

    void "a bean context that does not override the method creates the bean by the type and declared qualifier of the definition"() {
        given:
        ApplicationContext context = buildContext('test.Beans', BEANS)
        BeanDefinition<?> lower = context.getBeanDefinition(type(context, 'Text'), Qualifiers.byName('lower'))
        List<List<Object>> lookups = []
        // Calls the default method and passes every other call to the real context
        BeanContext beanContext = (BeanContext) Proxy.newProxyInstance(
            BeanContext.classLoader,
            [BeanContext] as Class[],
            { Object proxy, Method method, Object[] args ->
                if (method.isDefault() && method.name == 'createBean' && method.parameterTypes[0] == BeanDefinition) {
                    return InvocationHandler.invokeDefault(proxy, method, args)
                }
                if (method.name == 'createBean') {
                    lookups << (args as List)
                }
                return method.invoke(context, args)
            } as InvocationHandler
        )

        when:
        def text = beanContext.createBean(lower, 'MiXeD')

        then:
        text.value == 'mixed'
        lookups.size() == 1
        lookups[0][0] == type(context, 'Text')
        lookups[0][1] == Qualifiers.byName('lower')

        cleanup:
        context.close()
    }

    private static Class<?> type(ApplicationContext context, String name) {
        context.classLoader.loadClass('test.Beans$' + name)
    }
}
