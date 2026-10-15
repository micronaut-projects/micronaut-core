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
import jakarta.validation.ConstraintViolationException
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyExecutable
import org.intellij.lang.annotations.Language
import reactor.core.publisher.Flux
import spock.lang.Unroll

import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

class AsyncGeneratorAdviceSpec extends AbstractPythonTypeElementSpec {

    @Language("python")
    private static final String PYTHON_CODE = '''
from micronaut.python.aop import TestAround
from micronaut.aop import InterceptorBean, MethodInvocationContext
from micronaut.context.annotation import Executable
from jakarta.inject import Singleton
from jakarta.validation.constraints import NotBlank
from collections.abc import AsyncIterator
from typing import Annotated
import asyncio
import java

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")
InterceptionLog = java.type("io.micronaut.python.aop.InterceptionLog")

@InterceptorBean(TestAround)
class RecordingInterceptor(MethodInterceptor):
    def intercept(self, context: MethodInvocationContext):
        InterceptionLog.recordInterception(context.getMethodName())
        return context.proceed()

@Singleton
class StreamingService:
    def __init__(self):
        self.events = []

    @Executable
    async def plain(self, prefix: str) -> AsyncIterator[str]:
        for index in range(3):
            yield prefix + str(index)

    @TestAround
    async def advised(self, prefix: str) -> AsyncIterator[str]:
        async for item in self.plain(prefix):
            yield item

    @Executable
    async def validated(self, prefix: Annotated[str, NotBlank]) -> AsyncIterator[str]:
        async for item in self.plain(prefix):
            yield item

    @Executable
    async def collect(self, method: str, prefix: str) -> str:
        items = []
        async for item in getattr(self, method)(prefix):
            items.append(item)
        return "|".join(items)

    @Executable
    async def collect_bridge(self, method: str, prefix: str) -> str:
        return "|".join(await self.java_collect(method, prefix))

    @TestAround
    async def paused_advised(self, prefix: str) -> AsyncIterator[str]:
        try:
            self.events.append("yield0")
            yield prefix + "0"
            await asyncio.sleep(60)
            self.events.append("yield1")
            yield prefix + "1"
        finally:
            self.events.append("closed")

    @Executable
    async def paused_validated(self, prefix: Annotated[str, NotBlank]) -> AsyncIterator[str]:
        try:
            self.events.append("yield0")
            yield prefix + "0"
            await asyncio.sleep(60)
            self.events.append("yield1")
            yield prefix + "1"
        finally:
            self.events.append("closed")

    @Executable
    async def close_stream(self, method: str, bridge: bool) -> str:
        self.events = []
        if bridge:
            first = await self.java_first(method, "first-")
        else:
            items = getattr(self, method)("first-")
            first = await items.__anext__()
            await asyncio.sleep(0)
            await items.aclose()
        for attempt in range(100):
            if self.events[-1:] == ["closed"]:
                break
            await asyncio.sleep(0.01)
        return first + "|" + ",".join(self.events)
'''

    def setup() {
        InterceptionLog.reset()
    }

    @Unroll
    void "Python async for consumes every item of #method on independent calls"() {
        given:
        def context = buildContext(PYTHON_CODE, true)
        def service = getBean(context, "python.StreamingService")

        expect:
        await(service.collect(method, "first-")) == "first-0|first-1|first-2"
        await(service.collect(method, "second-")) == "second-0|second-1|second-2"
        InterceptionLog.methods() == (method == "advised" ? ["advised", "advised"] : [])

        cleanup:
        context?.close()

        where:
        method << ["plain", "advised", "validated"]
    }

    void "validation rejects an invalid generator argument"() {
        given:
        def context = buildContext(PYTHON_CODE, true)
        def service = getBean(context, "python.StreamingService")

        when:
        await(service.collect("validated", ""))

        then:
        def failure = thrown(ExecutionException)
        failure.cause instanceof ConstraintViolationException
        failure.cause.message == "validated.prefix: must not be blank"

        cleanup:
        context?.close()
    }

    @Unroll
    void "the Java Publisher bridge consumes every item of #method on independent calls"() {
        given:
        def context = buildContext(PYTHON_CODE, true)
        def service = getBean(context, "python.StreamingService")
        service.asPolyglotValue().putMember("java_collect", (ProxyExecutable) { Value[] args ->
            Flux.from(service."${args[0].asString()}"(args[1].asString())).collectList().toFuture()
        })

        expect:
        await(service.collect_bridge(method, "first-")) == "first-0|first-1|first-2"
        await(service.collect_bridge(method, "second-")) == "second-0|second-1|second-2"
        InterceptionLog.methods() == (method == "advised" ? ["advised", "advised"] : [])

        cleanup:
        context?.close()

        where:
        method << ["plain", "advised", "validated"]
    }

    private static String await(CompletionStage<String> stage) {
        stage.toCompletableFuture().get(10, TimeUnit.SECONDS)
    }

    @Unroll
    void "#consumer closes an active #method generator after its first item"() {
        given:
        def context = buildContext(PYTHON_CODE, true)
        def service = getBean(context, "python.StreamingService")
        service.asPolyglotValue().putMember("java_first", (ProxyExecutable) { Value[] args ->
            Flux.from(service."${args[0].asString()}"(args[1].asString())).next().toFuture()
        })

        expect: "the 60-second pause prevents exhaustion from masquerading as cancellation"
        await(service.close_stream(method, bridge)) == "first-0|yield0,closed"
        InterceptionLog.methods() == (method == "paused_advised" ? ["paused_advised"] : [])

        cleanup:
        context?.close()

        where:
        method             | bridge | consumer
        "paused_advised"  | false  | "Python aclose"
        "paused_validated"| false  | "Python aclose"
        "paused_advised"  | true   | "Java next"
        "paused_validated"| true   | "Java next"
    }
}
