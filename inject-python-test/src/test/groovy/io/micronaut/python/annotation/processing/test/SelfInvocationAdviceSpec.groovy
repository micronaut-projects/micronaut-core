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

import io.micronaut.python.aop.InterceptionLog
import org.intellij.lang.annotations.Language

import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit

/**
 * A method of a Python bean that calls another intercepted method of the same bean through
 * {@code self} must reach the interceptor chain, the way {@code this.method()} does in a Java
 * bean whose generated proxy subclass is the receiver.
 */
class SelfInvocationAdviceSpec extends AbstractPythonTypeElementSpec {

    private static final String INTERCEPTOR = '''
from micronaut.python.aop import TestAround
from micronaut.aop import InterceptorBean, MethodInvocationContext
from micronaut.context.annotation import Executable
from jakarta.inject import Singleton
import java

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")
InterceptionLog = java.type("io.micronaut.python.aop.InterceptionLog")

@InterceptorBean(TestAround)
class RecordingInterceptor(MethodInterceptor):
    def intercept(self, context: MethodInvocationContext):
        InterceptionLog.record(context.getMethodName())
        return context.proceed()
'''

    def setup() {
        InterceptionLog.reset()
    }

    void "a self-invocation of an intercepted method runs the interceptor chain"() {
        given:
        @Language("python") def pythonCode = INTERCEPTOR + '''
@Singleton
class GreetingService:
    last_self = None

    def __init__(self):
        self.calls = []

    @Executable
    def hello(self, name: str) -> str:
        self.calls.append("hello")
        greeting = self.greet("Hello " + name)
        return greeting + ("" if GreetingService.last_self is self else " (self identity lost)")

    @TestAround
    def greet(self, greeting: str) -> str:
        GreetingService.last_self = self
        self.calls.append("greet")
        return greeting

    @Executable
    def plain(self, value: str) -> str:
        self.calls.append("plain")
        return self.helper(value)

    def helper(self, value: str) -> str:
        self.calls.append("helper")
        return value + "!"

    @Executable
    def recorded_calls(self) -> list[str]:
        return list(self.calls)

@Singleton
class GreetingCaller:
    def __init__(self, service: GreetingService):
        self.service = service

    @Executable
    def call(self, name: str) -> str:
        return self.service.hello(name)
'''
        def context = buildContext(pythonCode)
        def service = getBean(context, "python.GreetingService")
        def caller = getBean(context, "python.GreetingCaller")

        when: "a Java caller invokes the outer method"
        def result = service.hello("Fred")

        then: "the nested self-invocation was intercepted and ran on the same Python object"
        result == "Hello Fred"
        InterceptionLog.methods() == ["greet"]
        service.recorded_calls() == ["hello", "greet"]

        when: "a Python caller invokes the outer method"
        InterceptionLog.reset()
        result = caller.call("Bob")

        then:
        result == "Hello Bob"
        InterceptionLog.methods() == ["greet"]

        when: "a method without advice is called through self"
        InterceptionLog.reset()
        result = service.plain("x")

        then: "no interceptor runs and the call is direct"
        result == "x!"
        InterceptionLog.methods().isEmpty()
        service.recorded_calls() == ["hello", "greet", "hello", "greet", "plain", "helper"]

        when: "the intercepted method is called directly"
        InterceptionLog.reset()
        result = service.greet("Hi")

        then:
        result == "Hi"
        InterceptionLog.methods() == ["greet"]

        cleanup:
        context?.close()
    }

    void "a self-invocation of an intercepted coroutine method runs the interceptor chain"() {
        given:
        @Language("python") def pythonCode = INTERCEPTOR + '''
import asyncio

@Singleton
class AsyncGreetingService:
    last_self = None

    @Executable
    async def hello(self, name: str) -> str:
        greeting = await self.greet("Hello " + name)
        return greeting + ("" if AsyncGreetingService.last_self is self else " (self identity lost)")

    @TestAround
    async def greet(self, greeting: str) -> str:
        AsyncGreetingService.last_self = self
        await asyncio.sleep(0)
        return greeting

@Singleton
class AsyncGreetingCaller:
    def __init__(self, service: AsyncGreetingService):
        self.service = service

    @Executable
    async def call(self, name: str) -> str:
        return await self.service.hello(name)
'''
        def context = buildContext(pythonCode)
        def service = getBean(context, "python.AsyncGreetingService")
        def caller = getBean(context, "python.AsyncGreetingCaller")

        when: "a Java caller invokes the outer coroutine method"
        CompletionStage<String> stage = service.hello("Fred")
        def result = stage.toCompletableFuture().get(10, TimeUnit.SECONDS)

        then: "the awaited self-invocation was intercepted"
        result == "Hello Fred"
        InterceptionLog.methods() == ["greet"]

        when: "a Python caller awaits the outer coroutine method"
        InterceptionLog.reset()
        CompletionStage<String> callerStage = caller.call("Bob")
        result = callerStage.toCompletableFuture().get(10, TimeUnit.SECONDS)

        then:
        result == "Hello Bob"
        InterceptionLog.methods() == ["greet"]

        when: "the intercepted coroutine method is awaited directly from Java"
        InterceptionLog.reset()
        CompletionStage<String> greetStage = service.greet("Hi")
        result = greetStage.toCompletableFuture().get(10, TimeUnit.SECONDS)

        then:
        result == "Hi"
        InterceptionLog.methods() == ["greet"]

        cleanup:
        context?.close()
    }

    void "self-invocations pass keyword and defaulted arguments to the intercepted method"() {
        given:
        @Language("python") def pythonCode = INTERCEPTOR + '''
@Singleton
class FormattingService:
    @Executable
    def format_all(self, name: str) -> str:
        return self.format(name, suffix="?") + "|" + self.format(name) + "|" + self.format(suffix="!", value=name)

    @TestAround
    def format(self, value: str, suffix: str = ".") -> str:
        return value + suffix
'''
        def context = buildContext(pythonCode)
        def service = getBean(context, "python.FormattingService")

        when:
        def result = service.format_all("a")

        then:
        result == "a?|a.|a!"
        InterceptionLog.methods() == ["format", "format", "format"]

        cleanup:
        context?.close()
    }
}
