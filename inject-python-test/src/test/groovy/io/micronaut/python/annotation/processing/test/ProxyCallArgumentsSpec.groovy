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

import io.micronaut.python.aop.InterceptionLog
import org.intellij.lang.annotations.Language

import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit

/**
 * A Python caller of an advised Python bean calls the proxy the way it calls the method: omitting
 * defaulted arguments and passing keyword arguments. The proxy binds the call through the Python
 * signature, so the interceptors see the full argument list of the generated Java method.
 */
class ProxyCallArgumentsSpec extends AbstractPythonTypeElementSpec {

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
        values = ",".join(str(value) for value in context.getParameterValues())
        InterceptionLog.recordInterception(context.getMethodName() + "(" + values + ")")
        return context.proceed()
'''

    def setup() {
        InterceptionLog.reset()
    }

    void "a Python caller omits defaulted arguments of an advised method"() {
        given:
        @Language("python") def pythonCode = INTERCEPTOR + '''
@Singleton
class FormattingService:
    @TestAround
    def format(self, value: str, prefix: str = "<", suffix: str | None = None) -> str:
        return prefix + value + (suffix or ">")

    @TestAround
    async def format_async(self, value: str, suffix: str = ".") -> str:
        return value + suffix

@Singleton
class FormattingCaller:
    def __init__(self, service: FormattingService):
        self.service = service

    @Executable
    def call(self, value: str) -> str:
        return self.service.format(value) + "|" + self.service.format(value, "[") + "|" + self.service.format(value, "[", "]")

    @Executable
    async def call_async(self, value: str) -> str:
        return await self.service.format_async(value)
'''
        def context = buildContext(pythonCode)
        def caller = getBean(context, "python.FormattingCaller")

        when:
        def result = caller.call("a")

        then: "omitted arguments take the Python defaults, which the interceptor sees"
        result == "<a>|[a>|[a]"
        InterceptionLog.methods() == ["format(a,<,None)", "format(a,[,None)", "format(a,[,])"]

        when: "the advised method is a coroutine"
        InterceptionLog.reset()
        CompletionStage<String> stage = caller.call_async("b")

        then:
        stage.toCompletableFuture().get(10, TimeUnit.SECONDS) == "b."
        InterceptionLog.methods() == ["format_async(b,.)"]

        cleanup:
        context?.close()
    }

    void "a Python caller passes keyword arguments to an advised method"() {
        given:
        @Language("python") def pythonCode = INTERCEPTOR + '''
@Singleton
class FormattingService:
    @TestAround
    def format(self, value: str, prefix: str = "<", suffix: str = ">") -> str:
        return prefix + value + suffix

@Singleton
class FormattingCaller:
    def __init__(self, service: FormattingService):
        self.service = service

    @Executable
    def call(self, value: str) -> str:
        return (self.service.format(value, suffix="]")
            + "|" + self.service.format(suffix="}", value=value, prefix="{"))

    @Executable
    def missing(self) -> str:
        try:
            return self.service.format(suffix="]")
        except TypeError as e:
            return str(e)

    @Executable
    def unexpected(self, value: str) -> str:
        try:
            return self.service.format(value, other="]")
        except TypeError as e:
            return str(e)

    @Executable
    def too_many(self, value: str) -> str:
        try:
            return self.service.format(value, "<", ">", "extra")
        except TypeError as e:
            return str(e)

    @Executable
    def duplicate(self, value: str) -> str:
        try:
            return self.service.format(value, value=value)
        except TypeError as e:
            return str(e)
'''
        def context = buildContext(pythonCode)
        def caller = getBean(context, "python.FormattingCaller")

        when:
        def result = caller.call("a")

        then: "keyword arguments take their place in the argument list, in any order"
        result == "<a]|{a}"
        InterceptionLog.methods() == ["format(a,<,])", "format(a,{,})"]

        when: "the call does not bind to the Python signature"
        InterceptionLog.reset()
        def missing = caller.missing()
        def unexpected = caller.unexpected("a")
        def duplicate = caller.duplicate("a")
        def tooMany = caller.too_many("a")

        then: "it fails as the Python call would, before any interceptor runs"
        missing.contains("missing required argument: 'value'")
        unexpected.contains("unexpected keyword argument 'other'")
        duplicate.contains("multiple values for argument 'value'")
        tooMany.contains("takes from 2 to 4 positional arguments but 5 were given")
        InterceptionLog.methods().isEmpty()

        cleanup:
        context?.close()
    }

    void "positional-only parameters of an advised method are bound positionally"() {
        given:
        @Language("python") def pythonCode = INTERCEPTOR + '''
@Singleton
class LookupService:
    @TestAround
    def lookup(self, key: str, scope: str = "global", /, suffix: str = ".") -> str:
        return scope + ":" + key + suffix

    @TestAround
    def outer(self, key: str) -> str:
        return self.lookup(key, suffix="!")

@Singleton
class LookupCaller:
    def __init__(self, service: LookupService):
        self.service = service

    @Executable
    def call(self) -> str:
        return (self.service.lookup("a") + "|" + self.service.lookup("a", "local")
            + "|" + self.service.lookup("a", suffix="?") + "|" + self.service.lookup("a", "local", "!"))

    @Executable
    def nested(self) -> str:
        return self.service.outer("b")

    @Executable
    def keyword(self) -> str:
        try:
            return self.service.lookup(key="a")
        except TypeError as e:
            return str(e)

    @Executable
    def keywords(self) -> str:
        try:
            return self.service.lookup("a", key="b", scope="local")
        except TypeError as e:
            return str(e)
'''
        def context = buildContext(pythonCode)
        def caller = getBean(context, "python.LookupCaller")

        when:
        def result = caller.call()

        then: "the positional-only parameters are arguments of the Java method, omitted ones taking their defaults"
        result == "global:a.|local:a.|global:a?|local:a!"
        InterceptionLog.methods() == ["lookup(a,global,.)", "lookup(a,local,.)", "lookup(a,global,?)", "lookup(a,local,!)"]

        when: "the method is called through self"
        InterceptionLog.reset()
        def nested = caller.nested()

        then:
        nested == "global:b!"
        InterceptionLog.methods() == ["outer(b)", "lookup(b,global,!)"]

        when: "a positional-only parameter is passed by keyword"
        InterceptionLog.reset()
        def keyword = caller.keyword()
        def keywords = caller.keywords()

        then: "it fails as the Python call would, before any interceptor runs"
        keyword.contains("got some positional-only arguments passed as keyword arguments: 'key'")
        keywords.contains("got some positional-only arguments passed as keyword arguments: 'key, scope'")
        InterceptionLog.methods().isEmpty()

        cleanup:
        context?.close()
    }

    void "a Python caller omits defaulted arguments of the methods of an introduction proxy"() {
        given: "a class with introduction advice: its methods are proxied on the introduction target"
        @Language("python") def pythonCode = '''
from abc import ABC, abstractmethod
from micronaut.aop import Around, InterceptorBean, Introduction, MethodInvocationContext
from micronaut.context.annotation import Executable
from jakarta.inject import Singleton
import java

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")
InterceptionLog = java.type("io.micronaut.python.aop.InterceptionLog")

@Around
@Introduction(interfaces="java.lang.Runnable")
def RunnableAround(cls):
    return cls

@InterceptorBean(RunnableAround)
class RunnableAroundInterceptor(MethodInterceptor):
    def intercept(self, context: MethodInvocationContext):
        if context.getMethodName() == "run":
            return None
        values = ",".join(str(value) for value in context.getParameterValues())
        InterceptionLog.recordInterception(context.getMethodName() + "(" + values + ")")
        if context.getMethodName() == "describe":
            return "described:" + values
        return context.proceed()

@RunnableAround
@Singleton
class Joiner(ABC):
    @Executable
    def join(self, first: str, second: str = "two", third: str | None = None) -> str:
        return first + "-" + second + "-" + str(third)

    @abstractmethod
    def describe(self, first: str, second: str = "two") -> str:
        pass

@Singleton
class JoinerCaller:
    def __init__(self, joiner: Joiner):
        self.joiner = joiner

    @Executable
    def call(self) -> str:
        return (self.joiner.join("one") + "|" + self.joiner.join("one", third="three")
            + "|" + self.joiner.describe("one") + "|" + self.joiner.describe(second="2", first="one"))
'''
        def context = buildContext(pythonCode)
        def caller = getBean(context, "python.JoinerCaller")

        when:
        def result = caller.call()

        then: "concrete and abstract methods bind to the signature the class declares"
        result == "one-two-None|one-two-three|described:one,two|described:one,2"
        InterceptionLog.methods() == ["join(one,two,None)", "join(one,two,three)", "describe(one,two)", "describe(one,2)"]

        cleanup:
        context?.close()
    }

    void "a @Cacheable key parameter combines with a defaulted parameter outside the key"() {
        given:
        @Language("python") def pythonCode = '''
from micronaut.cache.annotation import Cacheable, CacheConfig
from micronaut.context.annotation import Executable
from jakarta.inject import Singleton

class ProgressListener:
    def __init__(self, name: str):
        self.name = name

@Singleton
@CacheConfig("schedules")
class ScheduleCache:
    def __init__(self):
        self.computed = 0

    @Cacheable(parameters=["key"])
    async def schedule(self, key: str, interests: str, progress: ProgressListener | None = None) -> str:
        self.computed += 1
        return key + ":" + interests + ":" + (progress.name if progress else "-") + ":" + str(self.computed)

@Singleton
class ScheduleClient:
    def __init__(self, cache: ScheduleCache):
        self.cache = cache

    @Executable
    async def fetch(self, key: str, interests: str) -> str:
        return await self.cache.schedule(key, interests)

    @Executable
    async def fetch_with_progress(self, key: str, interests: str) -> str:
        return await self.cache.schedule(key, interests=interests, progress=ProgressListener("listener"))
'''
        def context = buildContext(pythonCode, true, ["micronaut.caches.schedules.maximum-size": "10"])
        def client = getBean(context, "python.ScheduleClient")

        when: "the defaulted progress parameter is omitted"
        def first = await(client.fetch("a", "x"))

        then:
        first == "a:x:-:1"

        when: "the same key is fetched with other interests, and with a progress listener"
        def second = await(client.fetch("a", "y"))
        def third = await(client.fetch_with_progress("a", "z"))

        then: "the cache key is the key parameter alone"
        second == "a:x:-:1"
        third == "a:x:-:1"

        when: "another key is fetched"
        def fourth = await(client.fetch_with_progress("b", "x"))

        then:
        fourth == "b:x:listener:2"

        cleanup:
        context?.close()
    }

    private static Object await(CompletionStage<?> stage) {
        stage.toCompletableFuture().get(10, TimeUnit.SECONDS)
    }
}
