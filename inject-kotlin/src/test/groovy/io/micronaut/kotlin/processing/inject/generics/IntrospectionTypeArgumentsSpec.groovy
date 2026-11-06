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
import io.micronaut.core.type.Argument
import io.micronaut.core.type.GenericPlaceholder

class IntrospectionTypeArgumentsSpec extends AbstractKotlinCompilerSpec {

    private static final String SOURCE = '''
package test

import io.micronaut.core.annotation.Introspected
import jakarta.inject.Singleton

interface Validator<A, T> {
    fun isValid(value: T): Boolean
}

interface StringIterable : Iterable<String>

@Introspected
@Singleton
class Bound : Validator<CharSequence, Number> {
    override fun isValid(value: Number): Boolean = true
}

@Introspected
@Singleton
class Indirect : StringIterable {
    override fun iterator(): Iterator<String> = emptyList<String>().iterator()
}

@Introspected
@Singleton
class Plain
'''

    // A Kotlin introspection of a subclass of java.util.HashMap or ArrayList does not load (duplicate property
    // methods), so those are read through the bean definition, which is written from the same map
    private static final String HIERARCHY = '''
package test

import io.micronaut.core.annotation.Introspected
import jakarta.inject.Singleton

interface Container<E, F>

interface Flipped<F, E> : Container<E, F>

open class Keyed<K, V> : Container<K, V>

@Introspected
@Singleton
class Reversed<A, B> : Keyed<B, A>()

@Introspected
@Singleton
class Swapped<K, V> : Keyed<V, K>()

@Introspected
@Singleton
class Holder<X, Y> : Flipped<X, Y>

@Singleton
class ReversedMap<A, B> : java.util.HashMap<B, A>()

@Singleton
class SwappedMap<K, V> : java.util.HashMap<V, K>()

@Singleton
class Strings : java.util.ArrayList<String>()
'''

    void "an introspected type reports the arguments it binds in an interface"() {
        given:
        def introspection = buildBeanIntrospection('test.Bound', SOURCE)
        def definition = buildBeanDefinition('test.Bound', SOURCE)

        expect:
        introspection.getTypeArguments('test.Validator')*.type == [CharSequence, Number]
        introspection.getTypeArguments('test.Validator') == definition.getTypeArguments('test.Validator')
    }

    void "arguments bound through an intermediate interface are reported"() {
        given:
        def introspection = buildBeanIntrospection('test.Indirect', SOURCE)

        expect:
        introspection.getTypeArguments(Iterable)*.type == [String]
    }

    void "an introspected type that binds nothing reports an empty list"() {
        given:
        def introspection = buildBeanIntrospection('test.Plain', SOURCE)

        expect:
        introspection.getTypeArguments() == []
        introspection.getTypeArguments('test.Validator') == []
    }

    void "a type two levels above is reported in the variables of the introspected type"() {
        given:
        def introspection = buildBeanIntrospection('test.Reversed', HIERARCHY)
        def definition = buildBeanDefinition('test.Reversed', HIERARCHY)

        expect:
        variableNames(introspection.getTypeArguments('test.Keyed')) == ['B', 'A']
        introspection.getTypeArguments('test.Container')*.name == ['E', 'F']
        variableNames(introspection.getTypeArguments('test.Container')) == ['B', 'A']
        variableNames(definition.getTypeArguments('test.Container')) == ['B', 'A']
        introspection.getTypeArguments('test.Container') == definition.getTypeArguments('test.Container')
    }

    void "a variable of the introspected type named like one of the super type is still bound by position"() {
        given:
        def introspection = buildBeanIntrospection('test.Swapped', HIERARCHY)

        expect:
        variableNames(introspection.getTypeArguments('test.Keyed')) == ['V', 'K']
        variableNames(introspection.getTypeArguments('test.Container')) == ['V', 'K']
    }

    void "a map two levels above a JDK collection is reported in the variables of the bean"() {
        expect:
        variableNames(buildBeanDefinition('test.ReversedMap', HIERARCHY).getTypeArguments(Map)) == ['B', 'A']
        variableNames(buildBeanDefinition('test.SwappedMap', HIERARCHY).getTypeArguments(Map)) == ['V', 'K']
    }

    void "an interface reached through an intermediate interface is reported in the variables of the introspected type"() {
        given:
        def introspection = buildBeanIntrospection('test.Holder', HIERARCHY)
        def definition = buildBeanDefinition('test.Holder', HIERARCHY)

        expect:
        variableNames(introspection.getTypeArguments('test.Flipped')) == ['X', 'Y']
        introspection.getTypeArguments('test.Container')*.name == ['E', 'F']
        variableNames(introspection.getTypeArguments('test.Container')) == ['Y', 'X']
        variableNames(definition.getTypeArguments('test.Container')) == ['Y', 'X']
    }

    void "a concrete type bound one level up is reported for every type above"() {
        given:
        def definition = buildBeanDefinition('test.Strings', HIERARCHY)

        expect:
        definition.getTypeArguments(Iterable)*.type == [String]
        !definition.getTypeArguments(Iterable)[0].isTypeVariable()
        definition.getTypeArguments(Collection)*.type == [String]
    }

    private static List<String> variableNames(List<Argument<?>> arguments) {
        arguments.collect { it instanceof GenericPlaceholder ? it.variableName : null }
    }
}
