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

import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery
import org.graalvm.polyglot.Value

/**
 * A method body that is only {@code ...} declares an abstract method in an ABC, a Protocol or an
 * introduction type; in a concrete bean it is a placeholder body that returns {@code None}.
 */
class PlaceholderMethodBodySpec extends AbstractPythonTypeElementSpec {

    void "test a concrete bean whose methods only have placeholder bodies is injectable and executable"() {
        given:
        def context = buildContext('''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable

@Singleton
class MessageListener:
    @Executable
    def on_message(self, message: str) -> None:
        ...

    @Executable
    def last_message(self) -> str:
        ...
''')

        when:
        def definition = getBeanDefinition(context, "python.MessageListener")
        def bean = getBean(context, "python.MessageListener")
        def onMessage = definition.findMethod("on_message", String).get()
        def lastMessage = definition.findMethod("last_message").get()

        then:
        !definition.isAbstract()
        onMessage.invoke(bean, "hello") == null
        lastMessage.invoke(bean) == null
        ((Value) bean.asPolyglotValue()).invokeMember("on_message", "hello").isNull()

        cleanup:
        context?.close()
    }

    void "test placeholder bodies of a concrete bean are concrete methods"() {
        expect:
        buildClassElement('''
from jakarta.inject import Singleton

@Singleton
class MessageListener:
    def on_message(self, message: str) -> None:
        ...
''', "MessageListener") { ClassElement classElement ->
            assert !classElement.isAbstract()
            assert !classElement.isInterface()
            def methods = classElement.getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared())
            assert methods.size() == 1
            assert !methods[0].isAbstract()
            return classElement
        }
    }

    void "test placeholder bodies of an ABC and a Protocol are abstract methods"() {
        expect:
        buildClassElement('''
from abc import ABC

class Operations(ABC):
    def run(self, value: str) -> str:
        ...
''', "Operations") { ClassElement classElement ->
            assert classElement.isAbstract()
            assert classElement.isInterface()
            assert classElement.getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared()).every { it.isAbstract() }
            return classElement
        }
        buildClassElement('''
from typing import Protocol

class Contract(Protocol):
    def run(self, value: str) -> str:
        ...
''', "Contract") { ClassElement classElement ->
            assert classElement.isAbstract()
            assert classElement.isInterface()
            assert classElement.getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared()).every { it.isAbstract() }
            return classElement
        }
        buildClassElement('''
from abc import abstractmethod
from jakarta.inject import Singleton

@Singleton
class Explicit:
    @abstractmethod
    def run(self, value: str) -> str:
        ...

    def concrete(self) -> str:
        ...
''', "Explicit") { ClassElement classElement ->
            assert classElement.isAbstract()
            def methods = classElement.getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared())
            assert methods.find { it.name == "run" }.isAbstract()
            assert !methods.find { it.name == "concrete" }.isAbstract()
            return classElement
        }
    }

    void "test placeholder bodies of an introduction type are implemented by the introduction advice"() {
        given:
        def context = buildContext('''
from micronaut.aop import InterceptorBean, Introduction, MethodInvocationContext
from micronaut.context.annotation import Executable
from jakarta.inject import Singleton
import java

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")

@Introduction
def Stub(cls):
    return cls

@InterceptorBean(Stub)
@Singleton
class StubInterceptor(MethodInterceptor):
    def intercept(self, context: MethodInvocationContext):
        if context.getMethodName() == "introduced":
            return "introduced"
        return context.proceed()

@Stub
@Singleton
class StubbedBean:
    def introduced(self) -> str:
        ...

    @Executable
    def concrete(self) -> str:
        return "concrete"
''')

        when:
        def definition = getBeanDefinition(context, "python.StubbedBean")
        Value bean = getBean(context, "python.StubbedBean").asPolyglotValue()

        then:
        bean.invokeMember("introduced").asString() == "introduced"
        bean.invokeMember("concrete").asString() == "concrete"
        definition.findMethod("concrete").isPresent()

        cleanup:
        context?.close()
    }
}
