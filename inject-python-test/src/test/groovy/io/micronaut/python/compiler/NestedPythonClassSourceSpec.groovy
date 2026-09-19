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
 * A class nested in a Python class compiles to a member type of the generated class of the enclosing class.
 */
class NestedPythonClassSourceSpec extends GeneratedJavaSourceSpec {

    void "a nested Python class is a static member type of the enclosing stub"() {
        given:
        def pythonCode = '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable


@Singleton
class Outer:

    class Inner:
        def greet(self) -> str:
            return "hi"

        class Innermost:
            def deep(self) -> str:
                return "deep"

    @Executable
    def inner(self) -> Inner:
        return Outer.Inner()
'''

        when:
        def sources = generatedSources(pythonCode)

        then: "no top-level source is written for the nested classes"
        sources.keySet().find { it.endsWith('/python/Outer.java') }
        !sources.keySet().find { it.endsWith('/python/Outer$Inner.java') }
        !sources.keySet().find { it.endsWith('/python/Outer$Inner$Innermost.java') }

        and: "the nested classes are static member types, referenced by their canonical names"
        def outer = sources.find { it.key.endsWith('/python/Outer.java') }.value
        outer.contains('public static class Inner implements ValueCoercible {')
        outer.contains('public static class Innermost implements ValueCoercible {')
        outer.contains('public Inner inner() {')
        outer.contains('return new python.Outer.Inner(arg1);')
        outer.contains('return new python.Outer.Inner.Innermost(arg1);')
        outer.contains('new String[]{"Inner"}, "Outer.Inner"')
        outer.contains('new String[]{"Inner","Innermost"}, "Outer.Inner.Innermost"')

        and: "the types generated from the stubs reference the member types"
        def mapping = sources.find { it.key.endsWith('/python/Outer$Inner$InnermostTargetTypeMapping.java') }.value
        mapping.contains('implements TargetTypeMapping<Outer.Inner.Innermost>')
        mapping.contains('return Outer.Inner.Innermost.fromPolyglotValue(value);')

        and: "the generated classes keep the binary names of nested types"
        def classLoader = compileToClassLoader(pythonCode)
        def inner = classLoader.loadClass('python.Outer$Inner')
        def innermost = classLoader.loadClass('python.Outer$Inner$Innermost')
        inner.isMemberClass()
        inner.getDeclaringClass() == classLoader.loadClass('python.Outer')
        java.lang.reflect.Modifier.isStatic(inner.modifiers)
        innermost.getDeclaringClass() == inner
    }

    void "a JUnit nested test class is an inner class of the enclosing test stub"() {
        given:
        def pythonCode = '''
from typing import Annotated

from jakarta.inject import Inject, Singleton
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Nested, Test


@Singleton
class OrderService:
    def place(self, item: str) -> str:
        return "placed " + item


@MicronautTest
class OrderServiceTest:

    order_service: Annotated[OrderService, Inject]

    @Test
    def test_places_an_order(self):
        assert self.order_service.place("book") == "placed book"

    @Nested
    class Placing:

        order_service: Annotated[OrderService, Inject]

        @Test
        def test_places_an_order_from_the_nested_test(self):
            assert self.order_service.place("pen") == "placed pen"
'''

        when:
        def sources = generatedSources(pythonCode)
        def test = sources.find { it.key.endsWith('/python/OrderServiceTest.java') }.value

        then: "the nested test class is a non-static inner class carrying the JUnit annotation"
        test.contains('@Nested')
        test.contains('public class Placing implements ValueCoercible, ValueCoercible.GeneratedPropertyMembers {')
        !test.contains('static class Placing')

        and: "it is a bean with the injected attribute, for the test extension to inject"
        def classLoader = compileToClassLoader(pythonCode)
        def placing = classLoader.loadClass('python.OrderServiceTest$Placing')
        placing.isMemberClass()
        !java.lang.reflect.Modifier.isStatic(placing.modifiers)
        placing.getAnnotation(org.junit.jupiter.api.Nested) != null
        classLoader.loadClass('python.$OrderServiceTest$Placing$Definition') != null
    }

    void "a nested enum stays a top-level type"() {
        given:
        def pythonCode = '''
from enum import Enum
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable


@Singleton
class Outer:

    class Colour(Enum):
        RED = "red"
        BLUE = "blue"

    @Executable
    def colour(self) -> Colour:
        return Outer.Colour.RED
'''

        when:
        def sources = generatedSources(pythonCode)

        then:
        sources.keySet().find { it.endsWith('/python/Outer$Colour.java') }
        sources.find { it.key.endsWith('/python/Outer.java') }.value.contains('public Outer$Colour colour() {')
    }

    private static Map<String, String> generatedSources(String pythonCode) {
        Map<String, String> sources = [:]
        compile(pythonCode).findAll { it.toUri().toString().contains("/SOURCE_OUTPUT/") && it.getKind() == JavaFileObject.Kind.SOURCE }
            .each { sources[it.toUri().toString()] = it.getCharContent(true).toString() }
        return sources
    }

    private static ClassLoader compileToClassLoader(String pythonCode) {
        return PyronautCompiler.builder()
            .pythonCode(pythonCode)
            .build()
            .buildClassLoader()
    }
}
