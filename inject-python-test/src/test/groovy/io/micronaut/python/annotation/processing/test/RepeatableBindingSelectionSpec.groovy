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

/**
 * Selection of interceptors bound by a repeatable binding annotation applied to Python classes.
 */
class RepeatableBindingSelectionSpec extends AbstractPythonTypeElementSpec {

    void "repeatable bindings on Python classes match every occurrence only when opted in: #tag"() {
        given:
        def context = buildContext("""
from io.micronaut.python.annotation.processing.test.aop import ${tag}
from micronaut.aop import MethodInvocationContext
from micronaut.context.annotation import Executable
from jakarta.inject import Singleton
import java

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")

@Singleton
@${tag}("a", note="interceptor")
@${tag}("b")
class TagInterceptor(MethodInterceptor):
    def intercept(self, context: MethodInvocationContext):
        return f"[tagged] {context.proceed()}"

@Singleton
@${tag}("a", note="target")
@${tag}("b")
class Target:
    def inherited(self) -> str:
        return "inherited"

    @${tag}("b")
    def narrowed(self) -> str:
        return "narrowed"

    @${tag}("a")
    @${tag}("b")
    @${tag}("c")
    def widened(self) -> str:
        return "widened"

@Singleton
class Caller:
    def __init__(self, target: Target):
        self.target = target

    @Executable
    def call(self) -> str:
        return ",".join([self.target.inherited(), self.target.narrowed(), self.target.widened()])
""")

        expect:
        getBean(context, "python.Caller").call() == expected

        cleanup:
        context?.close()

        where:
        tag      | expected
        'AnyTag' | '[tagged] inherited,[tagged] narrowed,[tagged] widened'
        'AllTag' | '[tagged] inherited,narrowed,[tagged] widened'
    }
}
