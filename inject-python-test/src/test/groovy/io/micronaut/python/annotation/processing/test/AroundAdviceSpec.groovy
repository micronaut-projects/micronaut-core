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
import spock.lang.Specification

/**
 * Tests for Python AOP Around advice.
 *
 * @author Micronaut
 * @since 4.8.0
 */
class AroundAdviceSpec extends AbstractPythonTypeElementSpec {

    void "test @TestAround on Python method modifies arguments"() {
        given:
        def pythonCode = '''
from micronaut.python.aop import TestAround
from micronaut.aop import MethodInterceptor, InterceptorBean, MethodInvocationContext

@InterceptorBean(TestAround)
class TestAroundInterceptor(MethodInterceptor):
    def intercept(self, context : MethodInvocationContext):
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
from micronaut.aop import MethodInterceptor, InterceptorBean, MethodInvocationContext

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
}
