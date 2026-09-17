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

import io.micronaut.aop.InterceptedProxy
import io.micronaut.context.python.ValueCoercible

/**
 * Scoped proxies (a {@code @Refreshable} factory method, which is what {@code @MockBean} is) of a
 * Python class must stand in for the bean the factory method returned, for Java and Python callers.
 * The contexts include every bean on the classpath: the refresh scope caching the target is one of them.
 */
class FactoryProxyPythonTargetSpec extends AbstractPythonTypeElementSpec {

    void "test scoped proxy of a Python class delegates to the factory bean"() {
        given:
        ScopedProxyInitCounter.reset()
        def pythonCode = '''
import java
from jakarta.inject import Singleton
from micronaut.context.annotation import Bean, Executable, Factory
from micronaut.runtime.context.scope import Refreshable

Counter = java.type("io.micronaut.python.annotation.processing.test.ScopedProxyInitCounter")

class MathService:
    def __init__(self):
        Counter.increment()
        self.result = 0
        self.calls: list[int] = []

    def compute(self, num: int) -> int:
        self.calls.append(num)
        return self.result

@Singleton
class MathController:
    def __init__(self, math_service: MathService):
        self.math_service = math_service

    @Executable
    def compute(self, num: int) -> int:
        return self.math_service.compute(num)

    @Executable
    def calls(self) -> list[int]:
        return list(self.math_service.calls)

@Factory
class MathServiceFactory:
    @Bean
    @Refreshable
    def math_service(self) -> MathService:
        service = MathService()
        service.result = 42
        return service
'''

        when:
        def context = buildContext(pythonCode, true)
        def proxy = getBean(context, "python.MathService")

        then: "the proxy is a lazy scoped proxy that has not created a Python object"
        proxy instanceof InterceptedProxy
        ScopedProxyInitCounter.count() == 0

        when: "a Python consumer receives the proxy without resolving the bean"
        def controller = getBean(context, "python.MathController")

        then:
        ScopedProxyInitCounter.count() == 0

        when: "the proxy resolves its target through the scope"
        def firstTarget = ((InterceptedProxy) proxy).interceptedTarget()
        def secondTarget = ((InterceptedProxy) proxy).interceptedTarget()

        then:
        firstTarget.is(secondTarget)
        ScopedProxyInitCounter.count() == 1

        when: "the Python consumer calls the factory bean through the proxy"
        def result = controller.compute(3)

        then:
        result == 42
        ScopedProxyInitCounter.count() == 1
        controller.calls() == [3]

        when: "the Python object of the proxy forwards to the factory bean"
        def target = ((InterceptedProxy) proxy).interceptedTarget()
        def proxyValue = ((ValueCoercible) proxy).asPolyglotValue()

        then:
        proxyValue.getMember("result").asInt() == 42
        proxyValue.getMember("calls").arraySize == 1
        ((ValueCoercible) target).asPolyglotValue().getMember("result").asInt() == 42
        ScopedProxyInitCounter.count() == 1

        when: "attributes are assigned through the Python object of the proxy"
        proxyValue.putMember("result", 9)
        controller.compute(4)

        then:
        proxyValue.getMember("result").asInt() == 9
        controller.calls() == [3, 4]
        ((ValueCoercible) target).asPolyglotValue().getMember("result").asInt() == 9
        ((ValueCoercible) target).asPolyglotValue().getMember("calls").arraySize == 2

        cleanup:
        context?.close()
    }

    void "test scoped proxy of a Python abstract base class implements the generated interface"() {
        given:
        def pythonCode = '''
from abc import ABC, abstractmethod
from jakarta.inject import Singleton
from micronaut.context.annotation import Bean, Executable, Factory
from micronaut.runtime.context.scope import Refreshable

class MathService(ABC):
    @abstractmethod
    def compute(self, num: int) -> int:
        ...

class MockMathService(MathService):
    def __init__(self):
        self.result = 0
        self.calls: list[int] = []

    def compute(self, num: int) -> int:
        self.calls.append(num)
        return self.result

@Singleton
class MathController:
    def __init__(self, math_service: MathService):
        self.math_service = math_service

    @Executable
    def compute(self, num: int) -> int:
        return self.math_service.compute(num)

    @Executable
    def calls(self) -> list[int]:
        return list(self.math_service.calls)

@Factory
class MathServiceFactory:
    @Bean
    @Refreshable
    def math_service(self) -> MathService:
        service = MockMathService()
        service.result = 42
        return service
'''

        when:
        def context = buildContext(pythonCode, true)
        def mathServiceType = context.classLoader.loadClass("python.MathService")
        def proxy = context.getBean(mathServiceType)
        def controller = getBean(context, "python.MathController")

        then: "the proxy implements the interface generated for the abstract base class"
        mathServiceType.isInterface()
        proxy instanceof InterceptedProxy
        !(proxy instanceof ValueCoercible)
        proxy.compute(2) == 42

        and: "a Python consumer of the proxy reaches the attributes of the factory bean through it"
        controller.compute(3) == 42
        controller.calls() == [2, 3]

        cleanup:
        context?.close()
    }
}
