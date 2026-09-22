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
}
