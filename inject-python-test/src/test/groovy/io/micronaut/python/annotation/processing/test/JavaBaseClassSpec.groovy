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
import io.micronaut.context.python.ValueCoercible
import io.micronaut.python.annotation.processing.test.javabases.AbstractCounter
import io.micronaut.python.annotation.processing.test.javabases.GreetingBase
import io.micronaut.python.annotation.processing.test.javabases.Services
import io.micronaut.python.compiler.PyronautCompiler
import org.graalvm.polyglot.Context

/**
 * Python classes extending Java classes: the generated Java class is the only Java instance of the
 * base, Python overrides are what Java callers reach, and the Python object reaches the inherited
 * Java methods, including {@code super()} delegation, through the Java instance.
 */
class JavaBaseClassSpec extends AbstractPythonTypeElementSpec {

    void "Python class extending a concrete Java class overrides, delegates to super and calls base methods"() {
        given:
        ApplicationContext ctx = buildContext('''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from micronaut.python.annotation.processing.test.javabases import GreetingBase


@Singleton
class PythonGreeter(GreetingBase):
    def __init__(self):
        super().__init__("python", 2)
        self.calls = 0

    def greet(self) -> str:
        self.calls += 1
        return "py:" + super().greet()

    @Executable
    def from_python(self) -> str:
        return self.describe() + "|" + self.finalGreeting() + "|" + self.protectedHook() + "|" + self.join("a") + "|" + self.join("b", 2) + "|" + self.join(3) + "|" + self.getName()

    @Executable
    def bump(self) -> int:
        return self.increment()

    @Executable
    def same(self) -> bool:
        return self.self() is self
''', true)

        when:
        GreetingBase greeter = ctx.getBean(GreetingBase)
        Class<?> generatedClass = ctx.classLoader.loadClass('python.PythonGreeter')

        then: 'the generated class extends the Java base and the Python override is what Java reaches'
        GreetingBase.isAssignableFrom(generatedClass)
        generatedClass.isInstance(greeter)
        greeter.greet() == 'py:Hello python x2'

        and: 'a Java method of the base that calls the overridden method reaches the Python override'
        greeter.describe() == 'describe:py:Hello python x2'
        greeter.self().is(greeter)

        and: 'Python calls the inherited public, final, protected and overloaded methods on itself'
        greeter.from_python() == 'describe:py:Hello python x2|final:python|hook:python|a|bb|***|python'
        greeter.same()

        and: 'the Java base state is shared between the Java and the Python view'
        greeter.bump() == 3
        greeter.increment() == 4
        greeter.greet() == 'py:Hello python x4'
        ((ValueCoercible) greeter).asPolyglotValue().getMember('calls').asInt() == 4

        cleanup:
        ctx?.close()
    }

    void "constructor arguments of the Python super call reach the Java base"() {
        given:
        ApplicationContext ctx = buildContext('''
from micronaut.python.annotation.processing.test.javabases import GreetingBase


class NamedGreeter(GreetingBase):
    def __init__(self, name: str, count: int):
        super().__init__(name.upper(), count + 1)
        self.original = name

    def greet(self) -> str:
        return "named:" + super().greet()
''', true)

        when: 'the instance is created from Java'
        Class<?> generatedClass = ctx.classLoader.loadClass('python.NamedGreeter')
        GreetingBase fromJava = generatedClass.getConstructor(String, int).newInstance('bob', 1)

        then:
        fromJava.greet() == 'named:Hello BOB x2'
        fromJava.getName() == 'BOB'
        ((ValueCoercible) fromJava).asPolyglotValue().getMember('original').asString() == 'bob'

        when: 'the instance is created in Python'
        Context polyglot = ctx.getBean(Context)
        def created = polyglot.eval('python', 'NamedGreeter("ann", 0)')
        def describedInPython = polyglot.eval('python', 'NamedGreeter("ann", 0).describe()').asString()
        GreetingBase fromPython = created.as(GreetingBase)

        then: 'the inherited Java method runs on the Java instance created for the Python object'
        describedInPython == 'describe:named:Hello ANN x1'
        fromPython.greet() == 'named:Hello ANN x1'
        fromPython.describe() == 'describe:named:Hello ANN x1'
        generatedClass.isInstance(fromPython)

        and: 'the Python object maps to one Java instance'
        created.as(GreetingBase).is(fromPython)
        created.as(generatedClass).is(fromPython)
        ((ValueCoercible) fromPython).asPolyglotValue().getMember('original').asString() == 'ann'

        cleanup:
        ctx?.close()
    }

    void "Python class extending an abstract Java class with state shares that state with Java"() {
        given:
        ApplicationContext ctx = buildContext('''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from micronaut.python.annotation.processing.test.javabases import AbstractCounter


@Singleton
class PythonCounter(AbstractCounter):
    def __init__(self):
        super().__init__("c")

    def label(self) -> str:
        return "L" + str(self.count())

    @Executable
    def twice(self) -> str:
        return self.next() + " " + self.next()
''', true)

        when:
        AbstractCounter counter = ctx.getBean(AbstractCounter)

        then:
        counter.next() == 'c1:L1'
        counter.twice() == 'c2:L2 c3:L3'
        counter.count() == 3
        counter.next() == 'c4:L4'

        cleanup:
        ctx?.close()
    }

    void "Python class extending a static nested Java class referenced through its outer class"() {
        given:
        ApplicationContext ctx = buildContext('''
from jakarta.inject import Singleton
from micronaut.python.annotation.processing.test.javabases import Services


@Singleton
class PythonService(Services.ServiceBase):
    def __init__(self):
        super().__init__("python")

    def handle(self, request: str) -> str:
        return "pong:" + request
''', true)

        when:
        Services.ServiceBase service = ctx.getBean(Services.ServiceBase)

        then:
        service.handle('x') == 'pong:x'
        service.describe() == 'python:pong:ping'

        cleanup:
        ctx?.close()
    }

    void "a Python class extending a final Java class is a compile error of that class only"() {
        when:
        PyronautCompiler.builder().verboseErrors(true).pythonCode('''
from jakarta.inject import Singleton
from micronaut.python.annotation.processing.test.javabases import FinalBase, GreetingBase


@Singleton
class Refused(FinalBase):
    pass


@Singleton
class AlsoRefused(FinalBase):
    pass


@Singleton
class Sibling(GreetingBase):
    def __init__(self):
        super().__init__("sibling", 1)
''').build().buildClassLoader()

        then: 'both refused classes are reported, so processing went on after the first; the sibling has no error'
        def e = thrown(RuntimeException)
        e.message.contains('Python class [Refused] cannot extend the final Java class [io.micronaut.python.annotation.processing.test.javabases.FinalBase]')
        e.message.contains('Python class [AlsoRefused] cannot extend the final Java class')
        !e.message.contains('Sibling')
        !e.message.contains('Native Python mode')
    }

    void "methods inherited from a non-public generic base class are visible on Java objects"() {
        given:
        ApplicationContext ctx = buildContext('''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from micronaut.python.annotation.processing.test.javabases import GenericBuilder


@Singleton
class BuilderUser:
    @Executable
    def build(self) -> str:
        return GenericBuilder.builder().stream("events").configuration(3).build()

    @Executable
    def build_with_string(self) -> str:
        builder = GenericBuilder.builder()
        return builder.configuration("four").stream("s").build()

    @Executable
    def has_stream(self) -> bool:
        return hasattr(GenericBuilder.builder(), "stream") and not hasattr(GenericBuilder.builder(), "missing")
''', true)

        when:
        def user = ctx.getBean(ctx.classLoader.loadClass('python.BuilderUser'))

        then:
        user.build() == 'events/3'
        user.build_with_string() == 's/4'
        user.has_stream()

        cleanup:
        ctx?.close()
    }
}
