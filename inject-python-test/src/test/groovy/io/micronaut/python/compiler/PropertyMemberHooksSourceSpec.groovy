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
package io.micronaut.python.compiler

import javax.tools.JavaFileObject

/**
 * A generated class overrides the property member hooks of ValueCoercible.GeneratedPropertyMembers
 * (micronautValueCoercibleSetMember, micronautValueCoerciblePutMember) only when the bodies do more
 * than the interface defaults: when the class writes property fields of its own (an introspected
 * class, which its subclasses are too), or when it must suppress the field-writing hooks of a Java
 * base. A class inheriting the defaults declares none.
 */
class PropertyMemberHooksSourceSpec extends GeneratedJavaSourceSpec {

    private static final String SET_MEMBER = 'public boolean micronautValueCoercibleSetMember(String key, Value value) {'
    private static final String PUT_MEMBER = 'public boolean micronautValueCoerciblePutMember(String key, Value value) {'

    void "a non-introspected class keeps the property name lookups and inherits the default hooks"() {
        given:
        def pythonCode = '''
class Counter:
    count: int = 0
    label: str = "counter"
'''

        when:
        def counter = generatedSource(pythonCode, 'Counter')

        then:
        counter.contains('implements ValueCoercible, ValueCoercible.GeneratedPropertyMembers {')
        counter.contains('public String micronautValueCoercibleGetterPropertyName(String key) {')
        counter.contains('public String micronautValueCoercibleSetterPropertyName(String key) {')
        !counter.contains(SET_MEMBER)
        !counter.contains(PUT_MEMBER)
    }

    void "an introspected class writes its property fields in the hooks"() {
        given:
        def pythonCode = '''
from dataclasses import dataclass
from micronaut.core.annotation import Introspected


@Introspected
@dataclass
class Point:
    x: int
    y: int
'''

        when:
        def point = generatedSource(pythonCode, 'Point')

        then:
        point.contains(SET_MEMBER)
        point.contains(PUT_MEMBER)
        point.contains('if ("x".equals(key)) {')
        point.contains('this.x = ')
    }

    void "a dataclass extending an introspected base is introspected itself and writes the fields of both in its hooks"() {
        given:
        def pythonCode = '''
from dataclasses import dataclass
from micronaut.core.annotation import Introspected


@Introspected
@dataclass
class Base:
    a: int


@dataclass
class Middle(Base):
    b: int = 2


@dataclass
class Leaf(Middle):
    c: int = 3
'''

        when:
        def middle = generatedSource(pythonCode, 'Middle')
        def leaf = generatedSource(pythonCode, 'Leaf')

        then: "@Introspected is inherited, so every subclass writes property fields of its own and keeps the hooks"
        middle.contains('public class Middle extends Base implements PooledValueCoercible, Serializable, ValueCoercible.GeneratedPropertyMembers {')
        middle.contains(SET_MEMBER)
        middle.contains(PUT_MEMBER)
        middle.contains('this.a = ')
        middle.contains('this.b = ')
        leaf.contains(SET_MEMBER)
        leaf.contains(PUT_MEMBER)
        leaf.contains('this.c = ')
    }

    void "a non-introspected class extending a non-introspected class declares no hooks"() {
        given:
        def pythonCode = '''
class Base:
    a: int = 1


class Sub(Base):
    b: int = 2
'''

        when:
        def sub = generatedSource(pythonCode, 'Sub')

        then:
        sub.contains('public class Sub extends Base implements ValueCoercible.GeneratedPropertyMembers {')
        sub.contains('public String micronautValueCoercibleGetterPropertyName(String key) {')
        !sub.contains(SET_MEMBER)
        !sub.contains(PUT_MEMBER)
    }

    void "dataclasses infer introspection and write declared and inherited property fields in their hooks"() {
        given:
        def pythonCode = '''
from dataclasses import dataclass

@dataclass
class Counter:
    count: int = 0
    label: str = "counter"

@dataclass
class Base:
    a: int = 1

@dataclass
class Sub(Base):
    b: int = 2
'''

        when:
        def counter = generatedSource(pythonCode, 'Counter')
        def sub = generatedSource(pythonCode, 'Sub')

        then:
        counter.contains(SET_MEMBER)
        counter.contains(PUT_MEMBER)
        counter.contains('if ("count".equals(key)) {')
        counter.contains('this.count = ')
        counter.contains('if ("label".equals(key)) {')
        counter.contains('this.label = ')
        sub.contains('public class Sub extends Base implements PooledValueCoercible, Serializable, ValueCoercible.GeneratedPropertyMembers {')
        sub.contains(SET_MEMBER)
        sub.contains(PUT_MEMBER)
        sub.contains('if ("a".equals(key)) {')
        sub.contains('this.a = ')
        sub.contains('if ("b".equals(key)) {')
        sub.contains('this.b = ')
    }

    private static String generatedSource(String pythonCode, String simpleName) {
        def outputs = compile(pythonCode)
        def source = outputs.find { it.toUri().toString().endsWith("/python/${simpleName}.java") && it.getKind() == JavaFileObject.Kind.SOURCE }
        assert source != null : "no generated source for ${simpleName}"
        source.getCharContent(true).toString()
    }
}
