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
package io.micronaut.inject.generics

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.AbstractInitializableBeanDefinition
import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.GenericPlaceholder
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Unroll

import static io.micronaut.inject.test.TypeArguments.render

/**
 * {@link BeanDefinition#getDeclaredBeanType()} is the bean type the way it was declared, where
 * {@link BeanDefinition#asArgument()} rebuilds it from the classes of its type arguments.
 */
class DeclaredBeanTypeSpec extends AbstractTypeElementSpec {

    private static final String SOURCE = '''
package test;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.util.List;

@Factory
class Producers {

    @Bean @Named("rawField") List rawField = null;

    @Bean @Named("strings") List<String> strings() { return null; }

    @Bean @Named("nested") List<List<String>> nested() { return null; }

    @Bean @Named("variable") <T extends Comparable<T>> List<T> variable() { return null; }

    @Bean @Named("raw") List raw() { return null; }

    @Bean @Named("array") List<String>[] array() { return null; }

    @Bean @Named("rawArray") List[] rawArray() { return null; }
}

@Singleton
class Plain {}

@Singleton
class Generic<T extends Number> {}
'''

    @Shared @AutoCleanup ApplicationContext context

    def setupSpec() {
        context = buildContext(SOURCE)
    }

    private BeanDefinition<?> produced(Class<?> type, String name) {
        context.getBeanDefinition(type, Qualifiers.byName(name))
    }

    private BeanDefinition<?> bean(String name) {
        context.getBeanDefinition(context.classLoader.loadClass(name))
    }

    @Unroll
    void "the bean type of the #name producer is declared as #declared"() {
        expect:
        render(produced(type, name).declaredBeanType) == declared

        where:
        name       | type   | declared
        'strings'  | List   | 'List<String>'
        'nested'   | List   | 'List<List<String>>'
        'variable' | List   | 'List<T extends Comparable<T extends Comparable>>'
        'raw'      | List   | 'List!raw<E extends Object>'
        'rawField' | List   | 'List!raw<E extends Object>'
        'array'    | List[] | 'List[]<String>'
        'rawArray' | List[] | 'List[]!raw<E extends Object>'
    }

    @Unroll
    void "the bean type of the #name producer as an argument is still #argument"() {
        expect:
        render(produced(type, name).asArgument()) == argument

        and: 'placeholders holding the classes of the type arguments, which is what they always were'
        produced(type, name).asArgument().typeParameters.every { it instanceof GenericPlaceholder && it.isTypeVariable() }

        where:
        name       | type   | argument
        'strings'  | List   | 'List<String=E>'
        'nested'   | List   | 'List<List=E>'
        'variable' | List   | 'List<Comparable=E>'
        'raw'      | List   | 'List<Object=E>'
        'array'    | List[] | 'List[]'
    }

    @Unroll
    void "the bean type of the class #name is declared as #declared"() {
        expect:
        render(bean(name).declaredBeanType) == declared

        where:
        name           | declared
        'test.Plain'   | 'Plain'
        'test.Generic' | 'Generic<T extends Number>'
    }

    void "a definition compiled before the rawness was recorded records no declaration of its bean type"() {
        expect:
        new AbstractInitializableBeanDefinition.PrecalculatedInfo(Optional.empty(), false, false, false, false, false, false, false, false).declaredBeanType() == null
        new AbstractInitializableBeanDefinition.PrecalculatedInfo(Optional.empty(), false, false, false, false, false, false, false).declaredBeanType() == null
    }
}
