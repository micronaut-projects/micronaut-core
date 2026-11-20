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
package io.micronaut.inject.qualifiers.replaces

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers

class ReplacesNamedBeanTypeSpec extends AbstractTypeElementSpec {

    private static final String BEANS = '''
package test;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

public class Beans {

    public interface Engine {
    }

    @Singleton
    @Named("old")
    public static class OldEngine implements Engine {
    }

    @Singleton
    @Replaces(bean = Engine.class, named = "old")
    public static class NewEngine implements Engine {
    }

    @Singleton
    @Named("old")
    public static class Wheel {
    }

    public record TextBook(String title) {
    }

    public record Novel(String title) {
    }

    @Factory
    public static class BookFactory {
        @Singleton
        TextBook textBook() {
            return new TextBook("old");
        }

        @Singleton
        Novel novel() {
            return new Novel("kept");
        }
    }

    @Factory
    public static class CustomBookFactory {
        @Singleton
        @Replaces(value = TextBook.class, factory = BookFactory.class)
        TextBook textBook() {
            return new TextBook("new");
        }
    }
}
'''

    void "a replacement that names a bean type and a name replaces only a bean of that type"() {
        given:
        ApplicationContext context = buildContext('test.Beans', BEANS)
        Class<?> wheel = type(context, 'Wheel')
        Class<?> oldEngine = type(context, 'OldEngine')

        expect:
        context.getBeanDefinitions(type(context, 'Engine'))*.beanType == [type(context, 'NewEngine')]
        context.getBeanDefinitions(Object)*.beanType.contains(wheel)
        !context.getBeanDefinitions(Object)*.beanType.contains(oldEngine)
        context.getBeanDefinitions(Qualifiers.byName('old'))*.beanType.contains(wheel)
        !context.getBeanDefinitions(Qualifiers.byName('old'))*.beanType.contains(oldEngine)

        cleanup:
        context.close()
    }

    void "a replacement that names a bean type and a factory replaces only a bean of that type"() {
        given:
        ApplicationContext context = buildContext('test.Beans', BEANS)
        Class<?> novel = type(context, 'Novel')

        expect:
        context.getBean(type(context, 'TextBook')).title() == 'new'
        context.getBeanDefinitions(Object)*.beanType.contains(novel)
        context.getBean(novel).title() == 'kept'

        cleanup:
        context.close()
    }

    private static Class<?> type(ApplicationContext context, String name) {
        context.classLoader.loadClass('test.Beans$' + name)
    }
}
