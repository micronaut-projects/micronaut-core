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
import io.micronaut.core.type.Argument
import io.micronaut.core.type.GenericPlaceholder

class IntrospectionTypeArgumentsSpec extends AbstractBeanDefinitionSpec {

    private static final String SOURCE = '''
package test

import io.micronaut.core.annotation.Introspected
import jakarta.inject.Singleton

interface Validator<A, T> {
    boolean isValid(T value)
}

interface StringIterable extends Iterable<String> {
}

@Introspected
@Singleton
class Bound implements Validator<CharSequence, Number> {
    boolean isValid(Number value) { true }
}

@Introspected
@Singleton
class Indirect implements StringIterable {
    Iterator<String> iterator() { null }
}

@Introspected
@Singleton
class Plain {
}
'''

    private static final String HIERARCHY = '''
package test

import io.micronaut.core.annotation.Introspected
import jakarta.inject.Singleton

interface Container<E, F> {
}

interface Flipped<F, E> extends Container<E, F> {
}

@Introspected
@Singleton
class Reversed<A, B> extends HashMap<B, A> {
}

@Introspected
@Singleton
class Swapped<K, V> extends HashMap<V, K> {
}

@Introspected
@Singleton
class Holder<X, Y> implements Flipped<X, Y> {
}

@Introspected
@Singleton
class Strings extends ArrayList<String> {
}
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
        variableNames(introspection.getTypeArguments(HashMap)) == ['B', 'A']
        introspection.getTypeArguments(Map)*.name == ['K', 'V']
        variableNames(introspection.getTypeArguments(Map)) == ['B', 'A']
        variableNames(definition.getTypeArguments(Map)) == ['B', 'A']
        introspection.getTypeArguments(Map) == definition.getTypeArguments(Map)
    }

    void "a variable of the introspected type named like one of the super type is still bound by position"() {
        given:
        def introspection = buildBeanIntrospection('test.Swapped', HIERARCHY)
        def definition = buildBeanDefinition('test.Swapped', HIERARCHY)

        expect:
        variableNames(introspection.getTypeArguments(HashMap)) == ['V', 'K']
        variableNames(introspection.getTypeArguments(Map)) == ['V', 'K']
        variableNames(definition.getTypeArguments(Map)) == ['V', 'K']
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
        def introspection = buildBeanIntrospection('test.Strings', HIERARCHY)
        def definition = buildBeanDefinition('test.Strings', HIERARCHY)

        expect:
        introspection.getTypeArguments(Iterable)*.type == [String]
        !introspection.getTypeArguments(Iterable)[0].isTypeVariable()
        introspection.getTypeArguments(Collection)*.type == [String]
        definition.getTypeArguments(Iterable)*.type == [String]
    }

    private static List<String> variableNames(List<Argument<?>> arguments) {
        arguments.collect { it instanceof GenericPlaceholder ? it.variableName : null }
    }
}
