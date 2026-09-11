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
package io.micronaut.python.annotation.processing.test

import io.micronaut.core.type.Argument
import io.micronaut.core.type.GenericPlaceholder

class IntrospectionTypeArgumentsSpec extends AbstractPythonTypeElementSpec {

    private static final String HIERARCHY = '''
from typing import Generic, TypeVar
from java.util import ArrayList, HashMap
from io.micronaut.python.annotation.processing.test import TypeArgumentFlipped
from io.micronaut.core.annotation import Introspected
from jakarta.inject import Singleton

A = TypeVar("A")
B = TypeVar("B")
K = TypeVar("K")
V = TypeVar("V")
X = TypeVar("X")
Y = TypeVar("Y")

@Introspected
@Singleton
class Reversed(HashMap[B, A], Generic[A, B]):
    pass

@Introspected
@Singleton
class Swapped(HashMap[V, K], Generic[K, V]):
    pass

@Introspected
@Singleton
class Holder(TypeArgumentFlipped[X, Y], Generic[X, Y]):
    pass

@Introspected
@Singleton
class Strings(ArrayList[str]):
    pass
'''

    void "a Java type two levels above is reported in the variables of the Python type"() {
        given:
        def introspection = buildBeanIntrospection("python.Reversed", HIERARCHY)
        def definition = buildBeanDefinition("python", "Reversed", HIERARCHY)

        expect:
        variableNames(introspection.getTypeArguments(HashMap)) == ["B", "A"]
        introspection.getTypeArguments(Map)*.name == ["K", "V"]
        variableNames(introspection.getTypeArguments(Map)) == ["B", "A"]
        variableNames(definition.getTypeArguments(Map)) == ["B", "A"]
        introspection.getTypeArguments(Map) == definition.getTypeArguments(Map)
    }

    void "a Python variable named like a Java super type variable is still bound by position"() {
        given:
        def introspection = buildBeanIntrospection("python.Swapped", HIERARCHY)
        def definition = buildBeanDefinition("python", "Swapped", HIERARCHY)

        expect:
        variableNames(introspection.getTypeArguments(HashMap)) == ["V", "K"]
        variableNames(introspection.getTypeArguments(Map)) == ["V", "K"]
        variableNames(definition.getTypeArguments(Map)) == ["V", "K"]
    }

    void "a Java interface reached through an intermediate interface is reported in the variables of the Python type"() {
        given:
        def introspection = buildBeanIntrospection("python.Holder", HIERARCHY)
        def definition = buildBeanDefinition("python", "Holder", HIERARCHY)

        expect:
        variableNames(introspection.getTypeArguments(TypeArgumentFlipped)) == ["X", "Y"]
        introspection.getTypeArguments(TypeArgumentContainer)*.name == ["E", "F"]
        variableNames(introspection.getTypeArguments(TypeArgumentContainer)) == ["Y", "X"]
        variableNames(definition.getTypeArguments(TypeArgumentContainer)) == ["Y", "X"]
    }

    void "a concrete Java type bound one level up is reported for every type above"() {
        given:
        def introspection = buildBeanIntrospection("python.Strings", HIERARCHY)
        def definition = buildBeanDefinition("python", "Strings", HIERARCHY)

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
