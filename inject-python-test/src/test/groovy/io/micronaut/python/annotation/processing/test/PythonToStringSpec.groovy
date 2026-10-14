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

import io.micronaut.context.ApplicationContext
import io.micronaut.core.beans.BeanIntrospection

/**
 * {@code __str__} (or, failing that, {@code __repr__}) of a Python class is the {@code toString()} of its
 * generated Java class, so Java code formatting the object (a map key of a serializer, a log statement,
 * a text/plain response) sees the Python representation.
 */
class PythonToStringSpec extends AbstractPythonTypeElementSpec {

    void "__str__ of a Python class is the toString of the generated class"() {
        given:
        ApplicationContext ctx = buildContext('''
from dataclasses import dataclass
from jakarta.inject import Singleton
from micronaut.core.annotation import Introspected


class Feature:
    def __init__(self, name: str):
        self._name = name

    def name(self) -> str:
        return self._name

    def __str__(self) -> str:
        return self._name


class Represented:
    def __repr__(self) -> str:
        return "Represented()"


class Plain:
    pass


@Introspected
@dataclass
class Point:
    x: int
    y: int

    def __str__(self) -> str:
        return f"({self.x}, {self.y})"


@Singleton
class Features:
    def tree(self) -> Feature:
        return Feature("Tree")

    def represented(self) -> Represented:
        return Represented()

    def plain(self) -> Plain:
        return Plain()
''', true)
        def features = ctx.getBean(ctx.classLoader.loadClass('python.Features'))

        expect: '__str__ is what String.valueOf sees'
        String.valueOf(features.tree()) == 'Tree'

        and: '__repr__ stands in when the class defines no __str__'
        features.represented().toString() == 'Represented()'

        and: 'a class defining neither keeps the default toString'
        features.plain().toString().startsWith('python.Plain@')

        and: 'the Java view of a dataclass formats its current state'
        BeanIntrospection introspection = getBeanIntrospection(ctx, 'python.Point')
        def point = introspection.instantiate(1, 2)
        point.toString() == '(1, 2)'
        introspection.getProperty('x').get().set(point, 3)
        point.toString() == '(3, 2)'

        cleanup:
        ctx?.close()
    }

    void "the generated toString follows Python's own resolution of str(obj)"() {
        given:
        ApplicationContext ctx = buildContext('''
from jakarta.inject import Singleton
from java.lang import String


class Described:
    def __init__(self, name: str):
        self.name = name

    def __str__(self) -> str:
        return "str:" + self.name

    def __repr__(self) -> str:
        return "repr:" + self.name


class OnlyRepresented(Described):
    def __repr__(self) -> str:
        return "sub-repr:" + self.name


class Inheriting(Described):
    pass


@Singleton
class Descriptions:
    def described(self) -> Described:
        return Described("a")

    def only_represented(self) -> OnlyRepresented:
        return OnlyRepresented("b")

    def inheriting(self) -> Inheriting:
        return Inheriting("c")

    def python_str(self, value: object) -> str:
        return str(value)
''', true)
        def descriptions = ctx.getBean(ctx.classLoader.loadClass('python.Descriptions'))
        def described = descriptions.described()
        def onlyRepresented = descriptions.only_represented()
        def inheriting = descriptions.inheriting()

        expect: '__str__ wins over __repr__ of the same class'
        described.toString() == 'str:a'

        and: 'a subclass defining only __repr__ still resolves to the inherited __str__, as str(obj) does'
        onlyRepresented.toString() == 'str:b'
        onlyRepresented.toString() == descriptions.python_str(onlyRepresented)

        and: 'a subclass defining neither has the representation of its base'
        inheriting.toString() == 'str:c'
        inheriting.toString() == descriptions.python_str(inheriting)

        cleanup:
        ctx?.close()
    }

    void "a Python method named toString takes precedence over __str__"() {
        given:
        ApplicationContext ctx = buildContext('''
from jakarta.inject import Singleton


class Named:
    def __init__(self, name: str):
        self.name = name

    def toString(self) -> str:
        return "java:" + self.name

    def __str__(self) -> str:
        return "python:" + self.name


@Singleton
class Names:
    def named(self) -> Named:
        return Named("x")
''', true)
        def names = ctx.getBean(ctx.classLoader.loadClass('python.Names'))

        expect: 'the bridge of the declared toString is the one Java calls'
        names.named().toString() == 'java:x'

        cleanup:
        ctx?.close()
    }

    void "an introspected dataclass defining no representation keeps the property based toString"() {
        given:
        ApplicationContext ctx = buildContext('''
from dataclasses import dataclass
from micronaut.core.annotation import Introspected


@Introspected
@dataclass
class Plain:
    name: str
    count: int
''', true)

        when:
        BeanIntrospection introspection = getBeanIntrospection(ctx, 'python.Plain')
        def plain = introspection.instantiate("a", 1)

        then: 'the generated toString lists the properties'
        plain.toString().contains('name=a')
        plain.toString().contains('count=1')

        cleanup:
        ctx?.close()
    }
}
