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

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.core.type.GenericPlaceholder
import io.micronaut.inject.BeanDefinition
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Unroll

import static io.micronaut.inject.test.TypeArguments.render

/**
 * A type argument that is a type variable left unresolved is the variable, with the name it was declared with and
 * every bound, the bounds written the way a type argument is; a type put in place of a variable is that type.
 */
class DeclaredTypeVariableSpec extends AbstractBeanDefinitionSpec {

    private static final String SOURCE = '''
package test

import io.micronaut.context.annotation.Executable
import jakarta.inject.Singleton

interface Payment {}
interface Refundable {}
interface Event<T> {}

@Singleton
class Bean<T extends Comparable<T>, U extends Payment & Refundable, N extends Number> {

    Bean(Event<T> recursive,
         Event<? super U> lowerVariable,
         Event<? extends U> upperVariable,
         Event<? extends List> rawBound,
         Event<List<N>> nested,
         Event<N[]> arrayOfVariable,
         N[] array) {}
}

@Singleton
class Base<X extends Number> {

    @Executable
    void observe(Event<X> event, X direct, X[] array, Event<List<X>> nested) {}
}

@Singleton
class Sub extends Base<Integer> {}
'''

    @Shared @AutoCleanup ApplicationContext context

    def setupSpec() {
        context = buildContext(SOURCE)
    }

    private BeanDefinition<?> definition(String name) {
        context.getBeanDefinition(context.classLoader.loadClass(name))
    }

    private Map<String, Argument<?>> constructorArguments() {
        definition('test.Bean').constructor.arguments.collectEntries { [it.name, it] }
    }

    private Map<String, Argument<?>> observed(String bean) {
        definition(bean).executableMethods.find { it.methodName == 'observe' }.arguments.collectEntries { [it.name, it] }
    }

    @Unroll
    void "the constructor argument #name is #rendered"() {
        expect:
        render(constructorArguments()[name]) == rendered

        where:
        name              | rendered
        'recursive'       | 'Event<T extends Comparable<T extends Comparable>>'
        'lowerVariable'   | 'Event<? super U extends Payment & Refundable>'
        'upperVariable'   | 'Event<? extends U extends Payment & Refundable>'
        'rawBound'        | 'Event<? extends List!raw<E extends Object>>'
        'nested'          | 'Event<List<N extends Number>>'
        'arrayOfVariable' | 'Event<(N extends Number)[]>'
        'array'           | '(N extends Number)[]'
    }

    void "a variable bounded by a type that names it keeps its own name and the variable its bound names"() {
        given:
        GenericPlaceholder<?> variable = (GenericPlaceholder<?>) constructorArguments().recursive.typeParameters[0]
        Argument<?> named = variable.bounds[0].typeParameters[0]

        expect: 'the variable itself, not the parameter of Event it stands in for'
        !variable.resolved
        variable.name == 'T'
        variable.variableName == 'T'

        and: 'its bound names it, rather than a Comparable it would erase to'
        variable.bounds*.type == [Comparable]
        named instanceof GenericPlaceholder
        !((GenericPlaceholder<?>) named).resolved
        ((GenericPlaceholder<?>) named).variableName == 'T'
    }

    @Unroll
    void "the observed #name of a class that leaves the variable unresolved is #rendered"() {
        expect:
        render(observed('test.Base')[name]) == rendered

        where:
        name     | rendered
        'event'  | 'Event<X extends Number>'
        'direct' | 'X extends Number'
        'array'  | '(X extends Number)[]'
        'nested' | 'Event<List<X extends Number>>'
    }

    @Unroll
    void "the observed #name of a subclass that resolves the variable is #rendered"() {
        expect:
        render(observed('test.Sub')[name]) == rendered

        where:
        name     | rendered
        'event'  | 'Event<Integer>'
        'direct' | 'Integer'
        'array'  | 'Integer[]'
        'nested' | 'Event<List<Integer>>'
    }
}
