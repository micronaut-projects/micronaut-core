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

import io.micronaut.python.annotation.processing.test.javabases.BoxedOverloadedBase
import io.micronaut.python.annotation.processing.test.javabases.OverloadedBase
import io.micronaut.python.annotation.processing.test.javabases.RoutingBase

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

    void "a type hint naming a subtype of one overload's parameter type selects that overload"() {
        given:
        def pythonCode = '''
from java.util import ArrayList
from jakarta.inject import Singleton
from io.micronaut.python.annotation.processing.test.javabases import OverloadedBase


@Singleton
class SubtypeRenderer(OverloadedBase):

    def render(self, values: ArrayList[str]) -> str:
        return "python:" + "|".join(values)
'''
        when: 'no overload names ArrayList, one names a supertype of it'
        def context = buildContext(pythonCode)
        OverloadedBase bean = getBean(context, "python.SubtypeRenderer") as OverloadedBase

        then: 'the stub declares the overload the hint is assignable to, not the hinted subtype'
        bean.class.declaredMethods.findAll { it.name == "render" && !it.bridge }*.parameterTypes == [[List] as Class[]]

        and: 'the selected overload runs the Python method'
        bean.render(["a", "b"]) == "python:a|b"

        and: 'the other overload keeps the base implementation, which delegates to the Python one'
        bean.render("abc") == "python:abc"

        cleanup:
        context?.close()
    }

    void "a type hint assignable to several overloads is a compile error"() {
        when: 'ArrayList names neither overload exactly and is assignable to both'
        buildContext('''
from java.util import ArrayList
from jakarta.inject import Singleton
from io.micronaut.python.annotation.processing.test.javabases import AssignableOverloadedBase


@Singleton
class AmbiguousAcceptor(AssignableOverloadedBase):

    def accept(self, values: ArrayList[str]) -> str:
        return "python"
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains('Python method [accept] of class [AmbiguousAcceptor] matches several overloads of [io.micronaut.python.annotation.processing.test.javabases.AssignableOverloadedBase]')
        e.message.contains('accept(java.util.Collection)')
        e.message.contains('accept(java.lang.Iterable)')
        e.message.contains('The type hints of the Python method are [accept(values: java.util.ArrayList[str])]')
        e.message.contains('narrow the type hint')
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

        then: 'the message names no match, the overloads that exist and the hints that were seen'
        def e = thrown(RuntimeException)
        e.message.contains('Python method [render] of class [AnyRenderer] matches none of the overloads of [io.micronaut.python.annotation.processing.test.javabases.OverloadedBase]')
        e.message.contains('render(java.lang.String)')
        e.message.contains('render(java.util.List)')
        e.message.contains('The type hints of the Python method are [render(values)]')
        e.message.contains('Python has no overloading: add a type hint to name the parameter types')
    }

    void 'a base method named with $ is left out and a Python class selects the overload taking a Class'() {
        given:
        def context = buildContext('''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from io.micronaut.python.annotation.processing.test.javabases import RoutingBase


@Singleton
class LoginView:
    pass


@Singleton
class PythonRouter(RoutingBase):
    def __init__(self):
        super().__init__()

    @Executable
    def route(self) -> None:
        self.open("login", LoginView)
        self.open("base", RoutingBase)
        self.open("text", "fallback")
''')
        RoutingBase router = getBean(context, "python.PythonRouter") as RoutingBase

        when:
        router.route()

        then: 'a Python class and a Java class reach the Class overload, a str the String one'
        router.routes == ['login->LoginView', 'base->RoutingBase', 'text~fallback']

        and: 'the method named with $ still runs its Java implementation from Java'
        router.$("button").routes.last() == '$button'

        cleanup:
        context?.close()
    }

    void "a type hint naming an unrelated type matches none of the overloads"() {
        when:
        buildContext('''
from jakarta.inject import Singleton
from io.micronaut.python.annotation.processing.test.javabases import OverloadedBase


@Singleton
class UnrelatedRenderer(OverloadedBase):

    def render(self, values: int) -> str:
        return "python"
''')

        then: 'the message names no match and the hint that was seen'
        def e = thrown(RuntimeException)
        e.message.contains('Python method [render] of class [UnrelatedRenderer] matches none of the overloads of [io.micronaut.python.annotation.processing.test.javabases.OverloadedBase]')
        e.message.contains('The type hints of the Python method are [render(values: int)]')
        e.message.contains('Python has no overloading: change the type hints to name the parameter types')
    }

    void "a type hint selects the overload it names without boxing when the base declares both"() {
        given:
        def context = buildContext("""
from jakarta.inject import Singleton
${pythonImport}
from io.micronaut.python.annotation.processing.test.javabases import BoxedOverloadedBase


@Singleton
class BoxedDescriber(BoxedOverloadedBase):

    def describe(self, value: ${hint}) -> str:
        return "python:" + str(value)
""")
        BoxedOverloadedBase bean = getBean(context, "python.BoxedDescriber") as BoxedOverloadedBase

        expect: 'the stub declares only the overload the hint names'
        bean.class.declaredMethods.findAll { it.name == "describe" && !it.bridge }*.parameterTypes == [[selected] as Class[]]

        and: 'the named overload runs the Python method, the other keeps its inherited implementation'
        BoxedOverloadedBase.getMethod("describe", selected).invoke(bean, 1) == "python:1"
        BoxedOverloadedBase.getMethod("describe", other).invoke(bean, 1) == inherited

        cleanup:
        context?.close()

        where:
        hint      | pythonImport                    | selected  | other     | inherited
        'int'     | ''                              | int.class | Integer   | 'integer:1'
        'Integer' | 'from java.lang import Integer' | Integer   | int.class | 'int:1'
    }

    void "a Python method hinting only some parameters is told to add or change its hints"() {
        when:
        buildContext('''
from jakarta.inject import Singleton
from io.micronaut.python.annotation.processing.test.javabases import TwoParameterOverloadedBase


@Singleton
class PartlyHintedJoiner(TwoParameterOverloadedBase):

    def join(self, prefix: str, values) -> str:
        return "python"
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains('Python method [join] of class [PartlyHintedJoiner] matches none of the overloads of [io.micronaut.python.annotation.processing.test.javabases.TwoParameterOverloadedBase]')
        e.message.contains('The type hints of the Python method are [join(prefix: str, values)]')
        e.message.contains('Python has no overloading: add or change the type hints to name the parameter types')
    }
}
