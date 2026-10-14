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

/**
 * {@code BeanDefinition#getTypeClosure()} for a bean declared in Python: the same record the Java processor
 * writes, see the spec of the same name in inject-java, for a hierarchy of Python classes and of Java bases.
 */
class TypeClosureSpec extends AbstractPythonTypeElementSpec {

    private static final String SOURCE = '''
from typing import Generic, TypeVar
from java.util import ArrayList
from jakarta.inject import Singleton

T = TypeVar("T")

class Pair[PK, PV]:
    pass

class Table[PK, PV](Pair[PK, PV]):
    pass

@Singleton
class Swapped[K, V](Table[V, K]):
    pass

@Singleton
class Strings(ArrayList[str]):
    pass

@Singleton
class Holder(Generic[T]):
    pass
'''

    void "a Python hierarchy is recorded with the variables bound along the path, Object last"() {
        given:
        def definition = buildBeanDefinition("python", "Swapped", SOURCE)
        def closure = definition.typeClosure

        expect:
        closure*.type*.name == ['python.Swapped', 'python.Table', 'python.Pair', Object.name]
        closure[0].typeParameters*.name == ['K', 'V']
        closure[1].typeParameters*.name == ['PK', 'PV']
        closure[2].typeParameters*.name == ['PK', 'PV']
        closure[3] == Argument.OBJECT_ARGUMENT

        and: 'the type argument lookups answer from the same record, with the variables bound by position as before'
        definition.getTypeArguments('python.Pair') == definition.getTypeArguments('python.Table')
    }

    void "a Java base is recorded with its own super types, each with the argument the Python type gives it"() {
        given:
        def definition = buildBeanDefinition("python", "Strings", SOURCE)
        def closure = definition.typeClosure
        def names = closure*.type*.name

        expect: 'the Python type first, the Java base and everything above it, Object once and last'
        names.first() == 'python.Strings'
        names.last() == Object.name
        names.toSet().size() == names.size()
        names.containsAll([ArrayList.name, AbstractList.name, List.name, Collection.name, Iterable.name])

        and: 'the concrete argument reaches every type above'
        closure.find { it.type == ArrayList }.typeParameters*.type == [String]
        closure.find { it.type == Iterable }.typeParameters*.type == [String]

        and: 'the type argument lookups answer from the same record'
        definition.getTypeArguments(Iterable)*.type == [String]
        definition.getTypeArguments(Iterable.name)*.type == [String]
    }

    void "an open variable of the Python type is recorded as the type argument map records it"() {
        given:
        def definition = buildBeanDefinition("python", "Holder", SOURCE)
        def closure = definition.typeClosure

        expect: 'a Python variable compiles to an argument named after it, erased to Object, as for the type arguments'
        closure*.type*.name == ['python.Holder', Object.name]
        closure[0].typeParameters*.name == ['T']
        closure[0].typeParameters*.type == [Object]
        definition.typeArguments == closure[0].typeParameters.toList()
    }
}
