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

import org.intellij.lang.annotations.Language

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * The proxy of an advised {@code @Prototype} Python bean creates a target on every call, from whichever thread makes
 * the call, and a failed call must not fail the ones after it.
 */
class ProxiedPrototypeConcurrencySpec extends AbstractPythonTypeElementSpec {

    void "concurrent calls of a proxied prototype all reach a target and later calls still do"() {
        given:
        @Language("python") def pythonCode = '''
from typing import Annotated
from micronaut.python.aop import TestAround
from micronaut.aop import InterceptorBean, MethodInvocationContext
from micronaut.context.annotation import Prototype, Executable
from jakarta.inject import Inject, Singleton
import java

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")

@InterceptorBean(TestAround)
class PassingInterceptor(MethodInterceptor):
    def intercept(self, context: MethodInvocationContext):
        return context.proceed()

@Singleton
class Repository:
    @Executable
    def name(self) -> str:
        return "repository"

@Prototype
class Helper:
    repository: Annotated[Repository, Inject]

@Prototype
class Service:
    repository: Annotated[Repository, Inject]
    helper: Annotated[Helper, Inject]

    @TestAround
    def find(self) -> str:
        return self.repository.name()
'''
        def context = buildContext(pythonCode)
        def service = getBean(context, "python.Service")
        def executor = Executors.newFixedThreadPool(8)
        def firstFailure = new AtomicReference<Throwable>()

        when: "each caller stops at its first failure: the message of a later one is too long to hold"
        def callers = (1..8).collect {
            executor.submit({
                try {
                    findRepeatedly(service, 200)
                } catch (Throwable e) {
                    firstFailure.compareAndSet(null, e)
                }
            } as Callable)
        }
        callers*.get(240, TimeUnit.SECONDS)

        then:
        firstFailure.get()?.message?.take(600) == null

        and:
        service.find() == "repository"

        cleanup:
        executor?.shutdownNow()
        context?.close()
    }

    private static void findRepeatedly(Object service, int times) {
        times.times {
            def found = service.find()
            if (found != "repository") {
                throw new IllegalStateException(String.valueOf(found))
            }
        }
    }
}
