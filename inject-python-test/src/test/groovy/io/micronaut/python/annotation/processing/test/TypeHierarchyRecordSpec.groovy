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
import io.micronaut.core.type.TypeHierarchies
import io.micronaut.core.type.TypeHierarchy
import io.micronaut.python.compiler.PyronautCompiler


import static io.micronaut.python.annotation.processing.test.TypeStrings.string
import static io.micronaut.python.annotation.processing.test.TypeStrings.strings

class TypeHierarchyRecordSpec extends AbstractPythonTypeElementSpec {

    static ClassLoader compile(String pythonCode) {
        PyronautCompiler.builder().pythonCode(pythonCode).build().buildClassLoader()
    }

    static TypeHierarchy record(ClassLoader classLoader, String typeName, String recordPackage = typeName.substring(0, typeName.lastIndexOf('.'))) {
        (TypeHierarchy) classLoader.loadClass(TypeHierarchies.recordClassName(typeName, recordPackage)).getConstructor().newInstance()
    }

    void "a generic Python class with a bounded variable is recorded"() {
        given:
        def classLoader = compile('''
from typing import Generic, TypeVar
from java.lang import Number
from java.util.function import Supplier
from micronaut.core.annotation import RecordTypeHierarchy

T = TypeVar("T", bound=Number)

class Base(Generic[T]):
    def value(self) -> T:
        return None

@RecordTypeHierarchy
class Payload(Base[T], Supplier[T], Generic[T]):
    def get(self) -> T:
        return None
''')
        def hierarchy = record(classLoader, 'python.Payload')

        expect:
        hierarchy.type == classLoader.loadClass('python.Payload')
        hierarchy.typeVariables.size() == 1
        hierarchy.typeVariables[0] instanceof GenericPlaceholder
        hierarchy.typeVariables[0].variableName == 'T'
        hierarchy.typeVariables[0].type == Number
        string(hierarchy.genericSuperclass.get()) == 'Base<T>'
        hierarchy.genericSuperclass.get().typeParameters[0].isUnresolvedTypeVariable()
        strings(hierarchy.genericInterfaces) == ['Supplier<T>']

        when:
        def closure = hierarchy.getTypeClosure(Argument.of(hierarchy.type, Integer))

        then:
        strings(closure) == ['Payload<Integer>', 'Base<Integer>', 'Supplier<Integer>', 'Object']
    }

    void "diamond interfaces and requested JDK classes"() {
        given:
        def classLoader = compile('''
from abc import ABC, abstractmethod
from typing import Generic, TypeVar
from java.util import ArrayList, AbstractList, List, Collection, AbstractCollection
from java.lang import Iterable
from micronaut.core.annotation import RecordTypeHierarchy

T = TypeVar("T")

# a Python class with abstract methods only compiles to a Java interface
class Top(ABC, Generic[T]):
    @abstractmethod
    def top(self) -> T:
        ...

class Left(Top[T], Generic[T]):
    @abstractmethod
    def left(self) -> T:
        ...

class Right(Top[T], Generic[T]):
    @abstractmethod
    def right(self) -> T:
        ...

@RecordTypeHierarchy(classes=[Top, Left, Right, Diamond, ArrayList, AbstractList, List, Collection, Iterable, AbstractCollection])
class Diamond(Left[str], Right[str]):
    def top(self) -> str:
        return "top"

    def left(self) -> str:
        return "left"

    def right(self) -> str:
        return "right"
''')
        def diamond = record(classLoader, 'python.Diamond')
        def arrayList = record(classLoader, 'java.util.ArrayList', 'python')

        expect:
        diamond.typeVariables.empty
        strings(diamond.genericInterfaces) == ['Left<String>', 'Right<String>']
        arrayList.type == ArrayList
        arrayList.typeVariables*.variableName == ['E']
        string(arrayList.genericSuperclass.get()) == 'AbstractList<E>'
        strings(arrayList.genericInterfaces) == ['List<E>', 'RandomAccess', 'Cloneable', 'Serializable']

        when:
        def closure = diamond.getTypeClosure(Argument.of(diamond.type))

        then:
        strings(closure) == ['Diamond', 'Left<String>', 'Top<String>', 'Right<String>', 'Object']

        when:
        closure = arrayList.getTypeClosure(Argument.of(ArrayList, Integer))

        then: "the interfaces List adds in later JDKs aside"
        strings(closure) - ['SequencedCollection<Integer>'] == ['ArrayList<Integer>', 'AbstractList<Integer>', 'AbstractCollection<Integer>', 'Collection<Integer>',
                                                                 'Iterable<Integer>', 'List<Integer>', 'RandomAccess', 'Cloneable', 'Serializable', 'Object']
    }
}
