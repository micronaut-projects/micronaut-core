/*
 * Copyright 2017-2025 original authors
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

import java.lang.reflect.Modifier

import io.micronaut.python.annotation.processing.test.bridge.ExtensionTest
import io.micronaut.python.annotation.processing.test.bridge.HttpFunctionLike
import io.micronaut.test.extensions.junit5.MicronautJunit5Extension
import jakarta.validation.constraints.Size
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.platform.commons.support.AnnotationSupport

/**
 * Every public method of a Python class is bridged to the generated Java class, whether or not the class is a bean
 * and whether or not the method carries an annotation Micronaut processes: compile-time visitors see the method on
 * the element and generate Java code that calls it, and frameworks instantiate the generated class reflectively.
 */
class PublicMethodBridgeSpec extends AbstractPythonTypeElementSpec {

    void "public methods of a plain Python class are bridged and delegate to the Python object"() {
        given:
        def context = buildContext('''
class TextFunctions:
    def shout(self, text: str) -> str:
        return text.upper()

    def repeat(self, text: str, times: int) -> str:
        return text * times

    def describe(self):
        return "text functions"

    def reset(self) -> None:
        self.was_reset = True

    def _hidden(self) -> str:
        return "hidden"

    def __call__(self, text: str) -> str:
        return text

    @staticmethod
    def whisper(text: str) -> str:
        return text.lower()
''')
        def type = context.classLoader.loadClass("python.TextFunctions")
        def instance = type.getConstructor().newInstance()

        expect:
        type.getMethod("shout", String).invoke(instance, "hello") == "HELLO"
        type.getMethod("repeat", String, int).invoke(instance, "ab", 2) == "abab"
        type.getMethod("describe").returnType == Object
        type.getMethod("describe").invoke(instance) == "text functions"
        type.getMethod("reset").returnType == Void.TYPE
        type.getMethod("reset").invoke(instance) == null
        instance.asPolyglotValue().getMember("was_reset").asBoolean()
        Modifier.isStatic(type.getMethod("whisper", String).modifiers)
        type.getMethod("whisper", String).invoke(null, "HELLO") == "hello"
        type.methods.every { !it.name.startsWith("_") }

        cleanup:
        context?.close()
    }

    void "public methods of a Python subclass of a host class are bridged for reflective callers"() {
        given:
        def context = buildContext('''
from micronaut.python.annotation.processing.test.bridge import HttpFunctionLike

class MyHttpFunction(HttpFunctionLike):
    def invoke(self, body: str) -> str:
        return self.functionName() + ": " + body

    def copy(self, source: str, target: str) -> None:
        self.copied = source + " -> " + target
''')
        def type = context.classLoader.loadClass("python.MyHttpFunction")
        def instance = type.getConstructor().newInstance()

        expect:
        HttpFunctionLike.isAssignableFrom(type)
        type.getMethod("invoke", String).invoke(instance, "payload") == "function: payload"
        type.getMethod("copy", String, String).invoke(instance, "a", "b") == null
        instance.asPolyglotValue().getMember("copied").asString() == "a -> b"

        cleanup:
        context?.close()
    }

    void "an un-annotated public method of a bean can be called from a visitor-generated Java class"() {
        given:
        def context = buildContext('''
from jakarta.inject import Singleton
from micronaut.python.annotation.processing.test.bridge import GenerateCaller

@Singleton
@GenerateCaller
class ShoutingService:
    def shout(self, text: str) -> str:
        return text.upper() + "!"

    def count(self, text: str) -> int:
        return len(text)

    def _internal(self) -> str:
        return "internal"
''')
        def service = getBean(context, "python.ShoutingService")
        def caller = context.classLoader.loadClass("python.ShoutingServiceCaller")

        expect:
        caller.getMethod("shout", service.class, String).invoke(null, service, "hey") == "HEY!"
        caller.getMethod("count", service.class, String).invoke(null, service, "hey") == 3
        caller.methods.every { it.name != "_internal" }
        service.class.methods.every { it.name != "_internal" }

        cleanup:
        context?.close()
    }

    void "an override whose return hint the Java override cannot repeat is served by the inherited bridge"() {
        given:
        def context = buildContext('''
from typing import TypeVar, Generic

T = TypeVar("T")

class Holder(Generic[T]):
    def value(self) -> T:
        raise NotImplementedError()

    def describe(self):
        return "holder"

class IntHolder(Holder[int]):
    def value(self) -> int:
        return 42

    def describe(self) -> int:
        return 1
''')
        def type = context.classLoader.loadClass("python.IntHolder")
        def instance = type.getConstructor().newInstance()

        expect:
        type.getMethod("value").declaringClass.name == "python.Holder"
        type.getMethod("value").invoke(instance) == 42
        type.getMethod("describe").declaringClass.name == "python.Holder"
        type.getMethod("describe").invoke(instance) == 1

        cleanup:
        context?.close()
    }

    void "a public method declaring its own type variable is bridged as a generic Java method"() {
        given:
        def context = buildContext('''
from typing import TypeVar, List

S = TypeVar("S")

class Lists:
    def singleton_list(self, item: S) -> List[S]:
        return [item]
''')
        def type = context.classLoader.loadClass("python.Lists")
        def instance = type.getConstructor().newInstance()
        def method = type.getMethod("singleton_list", Object)

        expect:
        method.typeParameters*.name == ["S"]
        method.genericReturnType.toString() == "java.util.List<S>"
        method.invoke(instance, "one") == ["one"]

        cleanup:
        context?.close()
    }

    void "a test annotation meta-annotated with ExtendWith is emitted on the generated test class"() {
        given:
        def context = buildContext('''
from typing import Annotated

from jakarta.inject import Inject
from micronaut.context import ApplicationContext
from micronaut.python.annotation.processing.test.bridge import ExtensionTest
from org.junit.jupiter.api import Test

@ExtensionTest(environments=["lambda"])
class ExtensionTestSpec:
    context: Annotated[ApplicationContext, Inject]

    @Test
    def test_context(self) -> None:
        assert self.context is not None
''')
        def type = context.classLoader.loadClass("python.ExtensionTestSpec")

        expect:
        type.getAnnotation(ExtensionTest) != null
        type.getAnnotation(ExtensionTest).environments() == ["lambda"] as String[]
        AnnotationSupport.findAnnotation(type, ExtendWith).map { it.value() as List }.orElse([]) == [MicronautJunit5Extension]
        type.getMethod("test_context").getAnnotation(org.junit.jupiter.api.Test) != null

        cleanup:
        context?.close()
    }

    void "a public method whose name or signature Java cannot declare is left out instead of failing the compilation"() {
        given: "legal Python method names that are Java keywords or clash with the final or incompatible methods of Object"
        def context = buildContext('''
class Helper:
    def notify(self) -> str:
        return "notified"

    def wait(self) -> None:
        pass

    def getClass(self) -> str:
        return "helper"

    def hashCode(self) -> str:
        return "hash"

    def toString(self, prefix: str) -> str:
        return prefix + "helper"

    def equals(self, other) -> bool:
        return other is self

    def default(self) -> str:
        return "default"

    def new(self) -> str:
        return "new"

    def describe(self) -> str:
        return "described"

    def clone(self) -> str:
        return "cloned"

    @staticmethod
    def wait_for(name: str) -> str:
        return "waited for " + name
''')
        def type = context.classLoader.loadClass("python.Helper")
        def instance = type.getConstructor().newInstance()

        expect: "the names Java cannot declare are not bridged"
        type.declaredMethods*.name.intersect(["notify", "wait", "getClass", "hashCode", "default", "new"]).isEmpty()

        and: "the Python object still has them"
        instance.asPolyglotValue().invokeMember("notify").asString() == "notified"
        instance.asPolyglotValue().invokeMember("default").asString() == "default"

        and: "overrides and overloads of Object methods that Java accepts are bridged"
        type.getMethod("describe").invoke(instance) == "described"
        type.getMethod("clone").invoke(instance) == "cloned"
        type.getMethod("toString", String).invoke(instance, "a ") == "a helper"
        type.getMethod("equals", Object).invoke(instance, instance)
        type.getMethod("wait_for", String).invoke(null, "it") == "waited for it"

        cleanup:
        context?.close()
    }

    void "an override of an Object method is bridged only when its return hint is a subtype of the Java return type"() {
        given: "toString without a hint (Object), with an incompatible hint and with the String hint Java accepts"
        def context = buildContext('''
class Plain:
    def toString(self):
        return "plain"


class Listed:
    def toString(self) -> list[str]:
        return ["a"]


class Named:
    def toString(self) -> str:
        return "named"
''')
        def plain = context.classLoader.loadClass("python.Plain").getConstructor().newInstance()
        def listed = context.classLoader.loadClass("python.Listed").getConstructor().newInstance()
        def named = context.classLoader.loadClass("python.Named").getConstructor().newInstance()

        expect: "an Object or a list return cannot override String toString(), the method stays on the Python object"
        plain.class.declaredMethods*.name.count("toString") == 0
        listed.class.declaredMethods*.name.count("toString") == 0
        plain.asPolyglotValue().invokeMember("toString").asString() == "plain"
        listed.asPolyglotValue().invokeMember("toString").getArrayElement(0).asString() == "a"

        and: "a String return is bridged and is what Java sees"
        named.class.getDeclaredMethod("toString").returnType == String
        String.valueOf(named) == "named"

        cleanup:
        context?.close()
    }

    void "a repeatable type annotation of a bridged method is emitted as the repeated annotation"() {
        given:
        def context = buildContext('''
from typing import Annotated
from jakarta.validation.constraints import Size

class Tagger:
    def tag(self, names: list[str]) -> Annotated[list[str], Size(min=1, max=3)]:
        return names
''')
        def type = context.classLoader.loadClass("python.Tagger")
        def method = type.getMethod("tag", List)

        expect: "the repeatable annotation is written on the return type, not its Size.List container"
        method.annotatedReturnType.getAnnotation(Size).min() == 1
        method.annotatedReturnType.getAnnotation(Size).max() == 3
        method.invoke(type.getConstructor().newInstance(), ["ab"]) == ["ab"]

        cleanup:
        context?.close()
    }
}
