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

import io.micronaut.python.annotation.processing.test.javabases.OverloadedBase

/**
 * A Python method overriding a Java base class method whose name carries several same-arity
 * overloads: the type hints select the one overload the Python method replaces, the others keep
 * the inherited implementation.
 */
class JavaBaseOverloadSpec extends AbstractPythonTypeElementSpec {

    void "type hints select the single base overload a Python method overrides"() {
        given:
        def pythonCode = '''
from jakarta.inject import Singleton
from io.micronaut.python.annotation.processing.test.javabases import OverloadedBase


@Singleton
class ListRenderer(OverloadedBase):

    def render(self, values: list[str]) -> str:
        return "python:" + "|".join(values)
'''
        when:
        def context = buildContext(pythonCode)
        OverloadedBase bean = getBean(context, "python.ListRenderer") as OverloadedBase

        then: 'only the hinted overload is declared by the stub'
        bean.class.declaredMethods.findAll { it.name == "render" && !it.bridge }*.parameterTypes == [[List] as Class[]]

        and: 'the overload the hints select runs the Python method'
        bean.render(["a", "b"]) == "python:a|b"

        and: 'the other overload keeps the base implementation, which delegates to the Python one'
        bean.render("abc") == "python:abc"
        bean.describe("abc") == "described:python:abc"

        cleanup:
        context?.close()
    }

    void "a Python method without hints for an overloaded base method is a compile error"() {
        when:
        buildContext('''
from jakarta.inject import Singleton
from io.micronaut.python.annotation.processing.test.javabases import OverloadedBase


@Singleton
class AnyRenderer(OverloadedBase):

    def render(self, values) -> str:
        return "python"
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains('Python method [render] of class [AnyRenderer] matches several overloads of [io.micronaut.python.annotation.processing.test.javabases.OverloadedBase]')
        e.message.contains('render(java.lang.String)')
        e.message.contains('render(java.util.List)')
        e.message.contains('type hint')
    }
}
