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

import io.micronaut.context.ApplicationContext
import io.micronaut.python.aop.TestAround
import spock.lang.PendingFeature
import spock.lang.Specification

/**
 * Tests for Python AOP Around advice.
 *
 * @author Micronaut
 * @since 4.8.0
 */
class AroundAdviceSpec extends AbstractPythonTypeElementSpec {

    void "test around advice with the decorator defined in Python and invoked from another type"() {
        given:
        def pythonCode = '''
from micronaut.aop import InterceptorBean, MethodInvocationContext, Around
from micronaut.context.annotation import Executable
from jakarta.inject import Singleton
import java

@Around
def NotNull(func):
    return func

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")

@InterceptorBean(NotNull)
class TestAroundInterceptor(MethodInterceptor):
    def intercept(self, context : MethodInvocationContext):
        for param in context.getParameters().values():
            if (param.getValue() is None):
                raise Exception(f"Null parameter [{param.getName()}] is not allowed")
        return context.proceed()


@Singleton
class Test:
    @NotNull
    def doWork(self, taskName : str):
        print(f"Doing job: {taskName}")

@Singleton
class TestCaller:
    def __init__(self, test : Test):
        self.test = test

    @Executable
    def doTest(self, taskName : str):
        self.test.doWork(taskName)

'''
        when:
        def context = buildContext(pythonCode)
        def testBean = getBean(context, "python.TestCaller")
        testBean.doTest(null)

        then:
        def e = thrown(RuntimeException)
        e.message == 'Exception: Null parameter [taskName] is not allowed'
    }

    void "test around advice will decorator defined in Python"() {
        given:
        def pythonCode = '''
from micronaut.aop import InterceptorBean, MethodInvocationContext, Around
from micronaut.context.annotation import Executable
from jakarta.inject import Singleton
import java

@Around
def NotNull(func):
    return func

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")

@InterceptorBean(NotNull)
class TestAroundInterceptor(MethodInterceptor):
    def intercept(self, context : MethodInvocationContext):
        for param in context.getParameters().values():
            if (param.getValue() is None):
                raise Exception(f"Null parameter [{param.getName()}] is not allowed")
        return context.proceed() + " processed"


@Singleton
class Test:
    @NotNull
    @Executable
    def doWork(self, taskName : str) -> str:
        print(f"Doing job: {taskName}")
        return taskName

'''
        def context = buildContext(pythonCode)
        def testBean = getBean(context, "python.Test")

        when:
        testBean.doWork(null)

        then:
        def e = thrown(RuntimeException)
        e.message == 'Exception: Null parameter [taskName] is not allowed'

        when:
        def result = testBean.doWork("Hello world")

        then:
        result == "Hello world processed"
    }

    void "test @TestAround on Python method modifies arguments"() {
        given:
        def pythonCode = '''
from micronaut.python.aop import TestAround
from micronaut.aop import InterceptorBean, MethodInvocationContext
import java

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")

@InterceptorBean(TestAround)
class TestAroundInterceptor(MethodInterceptor):
    def intercept(self, context : MethodInvocationContext):
        print("Hello World")
        # Modify string arguments to "intercepted"
        # Double numeric arguments
        for param_name, param_value in context.getParameters().items():
            if isinstance(param_value.getValue(), str):
                param_value.setValue("intercepted")
            elif isinstance(param_value.getValue(), (int, float)):
                param_value.setValue(param_value.getValue() * 2)
        return context.proceed()

@TestAround
class TestClass:
    def test_method(self, name: str, value: int) -> str:
        return f"Name: {name}, Value: {value}"

    def test_string_only(self, text: str) -> str:
        return f"Text: {text}"

    def test_number_only(self, num: int) -> int:
        return num * 10

    def test_no_args(self) -> str:
        return "no args"
'''

        when:
        def context = buildContext(pythonCode)
        def testBean = getBean(context, "python.TestClass")

        then:
        // Test method with both string and numeric arguments
        testBean.test_method("original", 5) == "Name: intercepted, Value: 10"

        // Test method with string only
        testBean.test_string_only("hello") == "Text: intercepted"

        // Test method with number only
        testBean.test_number_only(3) == 60  // 6 * 10

        // Test method with no args
        testBean.test_no_args() == "no args"

        cleanup:
        context?.close()
    }

    void "test @TestAround interceptor is properly registered"() {
        given:
        def pythonCode = '''
from micronaut.python.aop import TestAround
from micronaut.aop import InterceptorBean, MethodInvocationContext
import java

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")

@InterceptorBean(TestAround)
class TestAroundInterceptor(MethodInterceptor):
    def intercept(self, context : MethodInvocationContext):
        return f"intercepted: {context.proceed()}"

@TestAround
class TestClass:
    def greet(self, name: str) -> str:
        return f"Hello, {name}!"
'''

        when:
        def context = buildContext(pythonCode)

        then:
        // Verify the bean is created and intercepted
        def testBean = getBean(context, "python.TestClass")
        testBean.greet("World") == "intercepted: Hello, World!"

        cleanup:
        context?.close()
    }

    void "test @TestAround with a constructor"() {
        given:
        def pythonCode = '''
from micronaut.python.aop import TestAround
from micronaut.aop import InterceptorBean, MethodInvocationContext
from jakarta.inject import Singleton

import java

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")

@Singleton
class FooBar:
    def hello(self):
        return "World"

@InterceptorBean(TestAround)
class TestAroundInterceptor(MethodInterceptor):
    def intercept(self, context : MethodInvocationContext):
        return f"intercepted: {context.proceed()}"

@TestAround
class TestClass:

    def __init__(self, fooBar: FooBar):
        self.fooBar = fooBar

    def greet(self, name: str) -> str:
        return f"Hello, {name}!"
'''

        when:
        def context = buildContext(pythonCode)

        then:
        // Verify the bean is created and intercepted
        def testBean = getBean(context, "python.TestClass")
        testBean.greet("World") == "intercepted: Hello, World!"
        testBean.$unbox().hasMember("fooBar")
        testBean.$unbox().getMember("fooBar") != null
        testBean.$unbox().getMember("fooBar").invokeMember("hello").asString() == "World"

        cleanup:
        context?.close()
    }

    void "test method level interceptor matching"() {
        given:
        def pythonCode = '''
from micronaut.aop import InterceptorBean, MethodInvocationContext, Around
from jakarta.inject import Singleton
import java

@Around
def First(func):
    return func

@Around
def Second(func):
    return func

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")

@InterceptorBean(First)
class FirstInterceptor(MethodInterceptor):
    invoked: bool = False

    def intercept(self, context : MethodInvocationContext):
        self.invoked = True
        return context.proceed()

@InterceptorBean(Second)
class SecondInterceptor(MethodInterceptor):
    invoked: bool = False

    def intercept(self, context : MethodInvocationContext):
        self.invoked = True
        return context.proceed()

@Singleton
class Test:
    @First
    def first(self) -> str:
        return "first"

    @Second
    def second(self) -> str:
        return "second"
'''

        when:
        def context = buildContext(pythonCode)
        def testBean = getBean(context, "python.Test")
        def firstInterceptor = getBean(context, "python.FirstInterceptor")
        def secondInterceptor = getBean(context, "python.SecondInterceptor")
        def result = testBean.first()

        then:
        result == "first"
        firstInterceptor.invoked
        !secondInterceptor.invoked

        when:
        result = testBean.second()

        then:
        result == "second"
        secondInterceptor.invoked

        cleanup:
        context?.close()
    }

    @PendingFeature(reason = "Tracked in inject-python-test/DISABLED_TESTS.md: PY-INJECT-0054")
    void "test multiple around annotations on a single method"() {
        given:
        def pythonCode = '''
from micronaut.aop import InterceptorBean, MethodInvocationContext, Around
from jakarta.inject import Singleton
import java

@Around
def First(func):
    return func

@Around
def Second(func):
    return func

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")

@InterceptorBean(First)
class FirstInterceptor(MethodInterceptor):
    invoked: bool = False

    def intercept(self, context : MethodInvocationContext):
        self.invoked = True
        return context.proceed()

@InterceptorBean(Second)
class SecondInterceptor(MethodInterceptor):
    invoked: bool = False

    def intercept(self, context : MethodInvocationContext):
        self.invoked = True
        return context.proceed()

@Singleton
class Test:
    @First
    @Second
    def run(self) -> str:
        return "done"
'''

        when:
        def context = buildContext(pythonCode)
        def testBean = getBean(context, "python.Test")
        def firstInterceptor = getBean(context, "python.FirstInterceptor")
        def secondInterceptor = getBean(context, "python.SecondInterceptor")

        then:
        testBean.run() == "done"
        firstInterceptor.invoked
        secondInterceptor.invoked

        cleanup:
        context?.close()
    }

    @PendingFeature(reason = "Tracked in inject-python-test/DISABLED_TESTS.md: PY-INJECT-0055")
    void "test interceptor with multiple around bindings requires all method bindings"() {
        given:
        def pythonCode = '''
from micronaut.aop import InterceptorBean, MethodInvocationContext, Around
from jakarta.inject import Singleton
import java

@Around
def First(func):
    return func

@Around
def Second(func):
    return func

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")

@InterceptorBean([First, Second])
class BothInterceptor(MethodInterceptor):
    count: int = 0

    def intercept(self, context : MethodInvocationContext):
        self.count += 1
        return context.proceed()

@Singleton
class Test:
    @First
    def first(self) -> str:
        return "first"

    @Second
    def second(self) -> str:
        return "second"

    @First
    @Second
    def both(self) -> str:
        return "both"
'''

        when:
        def context = buildContext(pythonCode)
        def testBean = getBean(context, "python.Test")
        def interceptor = getBean(context, "python.BothInterceptor")
        def result = testBean.first()

        then:
        result == "first"
        interceptor.count == 0

        when:
        result = testBean.second()

        then:
        result == "second"
        interceptor.count == 0

        when:
        result = testBean.both()

        then:
        result == "both"
        interceptor.count == 1

        cleanup:
        context?.close()
    }

}
