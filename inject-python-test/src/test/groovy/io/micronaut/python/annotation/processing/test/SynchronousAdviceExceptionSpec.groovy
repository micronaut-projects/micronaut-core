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

class SynchronousAdviceExceptionSpec extends AbstractPythonTypeElementSpec {

    void "an intercepted Python exception returns to its caller with its type and identity"() {
        given:
        def context = buildContext('''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from micronaut.python.aop import TestAround
from micronaut.aop import InterceptorBean, MethodInvocationContext
import java

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")
IllegalStateException = java.type("java.lang.IllegalStateException")

@InterceptorBean(TestAround)
class Advice(MethodInterceptor):
    def intercept(self, context: MethodInvocationContext):
        return context.proceed()

class EmailAlreadyUsed(Exception):
    pass

@Singleton
class UserService:
    cause = ValueError("cause")
    error = EmailAlreadyUsed("duplicate email")

    @TestAround
    def create(self) -> str:
        raise self.error from self.cause

    @TestAround
    def builtin(self) -> str:
        raise ValueError("builtin")

    @TestAround
    def java_failure(self) -> str:
        raise IllegalStateException("java")

    @Executable
    def self_call(self) -> str:
        try:
            self.create()
        except EmailAlreadyUsed as error:
            return str(error)

@Singleton
class Controller:
    def __init__(self, service: UserService):
        self.service = service

    @Executable
    def signup(self) -> str:
        try:
            self.service.create()
        except EmailAlreadyUsed as error:
            assert error is self.service.error
            assert error.args == ("duplicate email",)
            assert error.__cause__ is self.service.cause
            assert error.__context__ is None
            assert error.__traceback__ is not None
            return "409"
        return "unexpected"

    @Executable
    def failures(self) -> str:
        try:
            self.service.builtin()
        except ValueError as error:
            assert str(error) == "builtin"
        try:
            self.service.java_failure()
        except IllegalStateException as error:
            return str(error.getMessage())
        return "unexpected"
''')

        expect:
        getBean(context, "python.Controller").signup() == "409"
        getBean(context, "python.Controller").failures() == "java"
        getBean(context, "python.UserService").self_call() == "duplicate email"

        when: "a Java caller still receives the generated exception type"
        getBean(context, "python.UserService").create()

        then:
        def failure = thrown(RuntimeException)
        failure.class.name == "python.EmailAlreadyUsed"
        failure.message == "duplicate email"

        cleanup:
        context?.close()
    }
}
