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
package io.micronaut.kotlin.processing.inject.generics

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
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
class DeclaredTypeVariableSpec extends AbstractKotlinCompilerSpec {

    private static final String SOURCE = '''
package test

import io.micronaut.context.annotation.Executable
import jakarta.inject.Singleton

interface Payment
interface Refundable
interface Event<T>

@Singleton
class Bean<T : Comparable<T>, U, N : Number>(
    recursive: Event<T>,
    lowerVariable: Event<in U>,
    upperVariable: Event<out U>,
    nested: Event<List<N>>,
    arrayOfVariable: Event<Array<N>>,
    array: Array<N>
) where U : Payment, U : Refundable

@Singleton
open class Base<X : Number> {

    @Executable
    open fun observe(event: Event<X>, direct: X, array: Array<X>, nested: Event<List<X>>) {}
}

@Singleton
class Sub : Base<Int>()
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

        and: 'while the type arguments of the placeholder are the ones it always had'
        !(variable.typeParameters[0] instanceof GenericPlaceholder)
        variable.typeParameters[0].type == Comparable
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
        given: 'the Kotlin processor compiles the type put in place of a variable into a placeholder named after it'
        Argument<?> argument = observed('test.Sub')[name]

        expect: 'which says it is the resolved type, and still names the variable and its bounds'
        render(argument) == rendered

        where:
        name     | rendered
        'event'  | 'Event<Integer=X>'
        'direct' | 'Integer=X'
        'array'  | 'Integer[]=X'
        'nested' | 'Event<List<Integer=X>>'
    }

    void "a type resolved in place of a variable keeps the bounds of the variable"() {
        given:
        GenericPlaceholder<?> resolved = (GenericPlaceholder<?>) observed('test.Sub').event.typeParameters[0]

        expect:
        resolved.resolved
        resolved.type == Integer
        resolved.variableName == 'X'
        resolved.bounds*.type == [Number]
    }
}
