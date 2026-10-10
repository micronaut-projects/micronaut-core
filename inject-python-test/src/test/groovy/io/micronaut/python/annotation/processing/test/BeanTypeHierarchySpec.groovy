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

class BeanTypeHierarchySpec extends AbstractPythonTypeElementSpec {

    void "an introspection describes the hierarchy of its type when asked to"() {
        given:
        def introspection = buildBeanIntrospection('python.Child', '''
from abc import ABC, abstractmethod
from typing import Generic, TypeVar
from java.io import Serializable
from micronaut.context.annotation import Executable
from micronaut.core.annotation import Introspected

T = TypeVar("T")

class Named(ABC):
    @abstractmethod
    def name(self) -> str:
        ...

class Titled(Named):
    @abstractmethod
    def title(self) -> str:
        ...

class Base(Named, Serializable, Generic[T]):
    @Executable
    def name(self) -> str:
        return "base"

    @Executable
    def value(self, input: T) -> T:
        return input

@Introspected(hierarchy=True)
class Child(Base[str], Titled):
    def title(self) -> str:
        return "title"

    @Executable
    def value(self, input: str) -> str:
        return input

    @Executable
    def own(self, amount: int) -> None:
        pass
''')
        def hierarchy = introspection.getTypeHierarchy().orElseThrow()
        def loader = introspection.beanType.classLoader
        def child = introspection.beanType
        def base = loader.loadClass('python.Base')
        def named = loader.loadClass('python.Named')
        def titled = loader.loadClass('python.Titled')
        def methods = introspection.beanMethods.collectEntries { [it.name, it] }

        expect:
        hierarchy.types == [child, base, named, Serializable, titled]
        hierarchy.getSuperclass(child).get() == base
        hierarchy.getSuperclass(base).get() == Object
        !hierarchy.getSuperclass(named).isPresent()
        hierarchy.getInterfaces(child) == [titled]
        hierarchy.getInterfaces(base) == [named, Serializable]
        hierarchy.getInterfaces(titled) == [named]
        methods.keySet() == ['name', 'value', 'own'] as Set
        hierarchy.declaredMethods*.name as Set == ['value', 'own'] as Set
        hierarchy.isDeclared(methods.value)
        hierarchy.isDeclared(methods.own)
        !hierarchy.isDeclared(methods.name)
        hierarchy.getDeclaringTypes(methods.value) == [child, base]
        hierarchy.getDeclaringTypes(methods.own) == [child]
        hierarchy.getDeclaringTypes(methods.name) == [base, named]
    }

    void "an inherited method lists an interface the introspected type introduces"() {
        when:
        def introspection = buildBeanIntrospection('python.Child', '''
from abc import ABC, abstractmethod
from micronaut.context.annotation import Executable
from micronaut.core.annotation import Introspected

class Deep(ABC):
    @abstractmethod
    def run(self) -> str:
        ...

class Contract(ABC):
    @abstractmethod
    def run(self) -> str:
        ...

class Parent(Deep):
    @Executable
    def run(self) -> str:
        return "parent"

@Introspected(hierarchy=True)
class Child(Parent, Contract):
    pass
''')
        def hierarchy = introspection.getTypeHierarchy().orElseThrow()
        def loader = introspection.beanType.classLoader
        def (parent, deep, contract) = ['Parent', 'Deep', 'Contract'].collect { loader.loadClass('python.' + it) }
        def run = introspection.beanMethods.find { it.name == 'run' }

        then:
        hierarchy.types == [introspection.beanType, parent, deep, contract]
        hierarchy.getDeclaringTypes(run) == [parent, contract, deep]
        !hierarchy.isDeclared(run)
    }

    void "the hierarchy of a Python type implementing a Java interface"() {
        when:
        def introspection = buildBeanIntrospection('python.Task', '''
from java.lang import Runnable
from micronaut.context.annotation import Executable
from micronaut.core.annotation import Introspected

@Introspected(hierarchy=True)
class Task(Runnable):
    @Executable
    def run(self) -> None:
        pass
''')
        def hierarchy = introspection.getTypeHierarchy().orElseThrow()
        def run = introspection.beanMethods.find { it.name == 'run' }

        then:
        hierarchy.types == [introspection.beanType, Runnable]
        hierarchy.getSuperclass(introspection.beanType).get() == Object
        hierarchy.getInterfaces(introspection.beanType) == [Runnable]
        hierarchy.getDeclaringTypes(run) == [introspection.beanType, Runnable]
        hierarchy.isDeclared(run)
    }

    void "an inherited method lists a Java interface the introspected type introduces"() {
        when:
        def introspection = buildBeanIntrospection('python.Child', '''
from java.lang import Runnable
from micronaut.context.annotation import Executable
from micronaut.core.annotation import Introspected

class Parent:
    @Executable
    def run(self) -> None:
        pass

@Introspected(hierarchy=True)
class Child(Parent, Runnable):
    pass
''')
        def hierarchy = introspection.getTypeHierarchy().orElseThrow()
        def parent = introspection.beanType.classLoader.loadClass('python.Parent')
        def run = introspection.beanMethods.find { it.name == 'run' }

        then:
        hierarchy.types == [introspection.beanType, parent, Runnable]
        hierarchy.getDeclaringTypes(run) == [parent, Runnable]
        !hierarchy.isDeclared(run)
    }

    void "the hierarchy of a Python type extending a Java class"() {
        when:
        def introspection = buildBeanIntrospection('python.Names', '''
from java.util import HashMap
from micronaut.context.annotation import Executable
from micronaut.core.annotation import Introspected

@Introspected(hierarchy=True)
class Names(HashMap[str, str]):
    @Executable
    def describe(self) -> str:
        return "names"
''')
        def hierarchy = introspection.getTypeHierarchy().orElseThrow()
        def describe = introspection.beanMethods.find { it.name == 'describe' }

        then:
        hierarchy.types == [introspection.beanType, HashMap, AbstractMap, Map, Cloneable, Serializable]
        hierarchy.getSuperclass(introspection.beanType).get() == HashMap
        hierarchy.getSuperclass(HashMap).get() == AbstractMap
        hierarchy.getSuperclass(AbstractMap).get() == Object
        hierarchy.getInterfaces(introspection.beanType) == []
        hierarchy.getDeclaringTypes(describe) == [introspection.beanType]
    }

    void "an introspection does not describe the hierarchy by default"() {
        expect:
        !buildBeanIntrospection('python.Plain', '''
from java.lang import Runnable
from micronaut.core.annotation import Introspected

@Introspected
class Plain(Runnable):
    def run(self) -> None:
        pass
''').getTypeHierarchy().isPresent()
    }
}
