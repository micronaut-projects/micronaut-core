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

class FactoryWithScopedProxySpec extends AbstractPythonTypeElementSpec {

    void "test refreshable factory bean is lazily initialized"() {
        given:
        ScopedProxyInitCounter.reset()
        def pythonCode = '''
import java
from micronaut.context.annotation import Bean, Executable, Factory
from micronaut.runtime.context.scope import Refreshable

Counter = java.type("io.micronaut.python.annotation.processing.test.ScopedProxyInitCounter")

class Test:
    def __init__(self):
        Counter.increment()

    @Executable
    def test(self) -> str:
        return "good"

@Factory
class TestFactory:
    @Bean
    @Refreshable
    def test(self) -> Test:
        return Test()
'''

        when:
        def context = buildContext(pythonCode)
        def bean = getBean(context, "python.Test")

        then:
        ScopedProxyInitCounter.count() == 1

        when:
        def result = bean.test()

        then:
        result == "good"
        ScopedProxyInitCounter.count() == 2

        cleanup:
        context?.close()
    }

    void "test refreshable factory beans resolve by generic type argument"() {
        given:
        ScopedProxyInitCounter.reset()
        def pythonCode = '''
from typing import Generic, TypeVar
import java
from micronaut.context.annotation import Bean, Executable, Factory
from micronaut.runtime.context.scope import Refreshable

Counter = java.type("io.micronaut.python.annotation.processing.test.ScopedProxyInitCounter")
T = TypeVar("T")

class Test(Generic[T]):
    def __init__(self):
        Counter.increment()

    @Executable
    def value(self) -> T:
        raise NotImplementedError()

class StringTest(Test[str]):
    def value(self) -> str:
        return "good"

class IntegerTest(Test[int]):
    def value(self) -> int:
        return 1

@Factory
class TestFactory:
    @Bean
    @Refreshable
    def test_string(self) -> Test[str]:
        return StringTest()

    @Bean
    @Refreshable
    def test_integer(self) -> Test[int]:
        return IntegerTest()
'''

        when:
        def context = buildContext(pythonCode)
        def testType = context.classLoader.loadClass("python.Test")
        def stringBean = context.getBean(io.micronaut.core.type.Argument.of(testType, String))
        def integerBean = context.getBean(io.micronaut.core.type.Argument.of(testType, Integer))

        then:
        stringBean.value() == "good"
        integerBean.value() == 1

        cleanup:
        context?.close()
    }
}
