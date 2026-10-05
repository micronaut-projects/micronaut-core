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

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit

/**
 * The exception an intercepted {@code async def} fails with reaches a Python caller awaiting it as
 * itself: the interceptor chain carries the failure through a Java {@link CompletionStage}, which
 * wraps it in a {@link java.util.concurrent.CompletionException} around the
 * {@link org.graalvm.polyglot.PolyglotException} of the Python exception.
 */
class CoroutineAdviceExceptionSpec extends AbstractPythonTypeElementSpec {

    @Language("python")
    private static final String PYTHON_CODE = '''
from micronaut.python.aop import TestAround
from micronaut.aop import InterceptorBean, MethodInvocationContext
from micronaut.context.annotation import Executable
from jakarta.inject import Singleton
import asyncio
import java

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")
InterceptionLog = java.type("io.micronaut.python.aop.InterceptionLog")
CompletableFuture = java.type("java.util.concurrent.CompletableFuture")
TimeUnit = java.type("java.util.concurrent.TimeUnit")
Function = java.type("java.util.function.Function")

@InterceptorBean(TestAround)
class StageInterceptor(MethodInterceptor):
    def intercept(self, context: MethodInvocationContext):
        InterceptionLog.recordInterception(context.getMethodName())
        stage = context.proceed()
        if context.getMethodName().startswith("delayed"):
            # completes on another thread: the failure reaches the caller as a CompletionException
            return stage.toCompletableFuture().thenApplyAsync(Function.identity(), CompletableFuture.delayedExecutor(50, TimeUnit.MILLISECONDS))
        return stage

class RejectedSchedule(Exception):
    def __init__(self, response):
        super().__init__("rejected")
        self.response = response

@Singleton
class ScheduleCache:
    last_rejection = None

    @TestAround
    async def schedule(self, key: str) -> str:
        await asyncio.sleep(0)
        ScheduleCache.last_rejection = RejectedSchedule("response for " + key)
        raise ScheduleCache.last_rejection

    @TestAround
    async def delayed_schedule(self, key: str) -> str:
        ScheduleCache.last_rejection = RejectedSchedule("delayed response for " + key)
        raise ScheduleCache.last_rejection

@Singleton
class ScheduleController:
    def __init__(self, cache: ScheduleCache):
        self.cache = cache

    @Executable
    async def schedule(self, key: str) -> str:
        try:
            return await self.cache.schedule(key)
        except RejectedSchedule as rejected:
            return rejected.response + (" (same)" if rejected is ScheduleCache.last_rejection else " (copy)")

    @Executable
    async def delayed_schedule(self, key: str) -> str:
        try:
            return await self.cache.delayed_schedule(key)
        except RejectedSchedule as rejected:
            return rejected.response + (" (same)" if rejected is ScheduleCache.last_rejection else " (copy)")
'''

    def setup() {
        InterceptionLog.reset()
    }

    void "a Python exception raised by an intercepted coroutine method is caught by type in an awaiting Python caller"() {
        given:
        def context = buildContext(PYTHON_CODE)
        def controller = getBean(context, "python.ScheduleController")

        when:
        CompletionStage<String> stage = controller.schedule("devoxx")

        then: "the awaiting caller caught the exception object the coroutine raised"
        stage.toCompletableFuture().get(10, TimeUnit.SECONDS) == "response for devoxx (same)"
        InterceptionLog.methods() == ["schedule"]

        when: "the interceptor completes the stage on another thread"
        InterceptionLog.reset()
        CompletionStage<String> delayedStage = CompletableFuture.supplyAsync { controller.delayed_schedule("devoxx") }.get(5, TimeUnit.SECONDS)

        then:
        delayedStage.toCompletableFuture().get(10, TimeUnit.SECONDS) == "delayed response for devoxx (same)"
        InterceptionLog.methods() == ["delayed_schedule"]

        cleanup:
        context?.close()
    }
}
