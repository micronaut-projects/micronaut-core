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

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer

/**
 * A Python exception extending a Java exception class keeps the arguments of its
 * {@code super().__init__(...)} call when it crosses into Java: the generated Java class
 * forwards them to the matching Java super constructor.
 */
class PythonExceptionSubclassSpec extends AbstractPythonTypeElementSpec {

    void "a Python RuntimeException subclass raised from a bean method keeps its message in Java"() {
        given:
        def context = buildContext('''
from java.lang import RuntimeException
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable


class NotEligibleException(RuntimeException):

    def __init__(self, message: str):
        super().__init__(message)


@Singleton
class RegistrationService:

    @Executable
    def register(self, name: str) -> str:
        if name == "Bob":
            raise NotEligibleException(name + " must be an adult")
        return "registered " + name
''')
        def service = getBean(context, "python.RegistrationService")
        def exceptionType = context.classLoader.loadClass("python.NotEligibleException")

        when:
        def result = service.register("Ann")

        then:
        result == "registered Ann"

        when:
        service.register("Bob")

        then:
        def e = thrown(RuntimeException)
        exceptionType.isInstance(e)
        e.message == "Bob must be an adult"

        when: "the exception is created from Java"
        def created = exceptionType.getConstructor(String).newInstance("from Java")

        then:
        created.message == "from Java"
        created.asPolyglotValue().getMember("args").getArrayElement(0).asString() == "from Java"

        cleanup:
        context?.close()
    }

    void "a Python exception subclass without a constructor gets its message from str(exception)"() {
        given:
        def context = buildContext('''
from java.lang import RuntimeException
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable


class OutOfStockException(RuntimeException):
    pass


@Singleton
class StockService:

    @Executable
    def stock(self, isbn: str) -> int:
        if isbn == "none":
            raise OutOfStockException()
        raise OutOfStockException("No stock for " + isbn)
''')
        def service = getBean(context, "python.StockService")
        def exceptionType = context.classLoader.loadClass("python.OutOfStockException")

        when:
        service.stock("1234")

        then:
        def e = thrown(RuntimeException)
        exceptionType.isInstance(e)
        e.message == "No stock for 1234"

        when:
        service.stock("none")

        then:
        def noArgs = thrown(RuntimeException)
        exceptionType.isInstance(noArgs)
        noArgs.message == null

        cleanup:
        context?.close()
    }

    void "the arguments of the Python super constructor call reach a multi-argument Java constructor"() {
        given:
        def context = buildContext('''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from micronaut.python.annotation.processing.test import AbstractProblemLike

TYPE = "https://example.org/" + "not-found"


class TaskNotFoundProblem(AbstractProblemLike):

    def __init__(self, task_id: int):
        super().__init__(TYPE, "Not found", 404, f"Task '{task_id}' not found")


@Singleton
class TaskService:

    @Executable
    def find(self, task_id: int) -> str:
        raise TaskNotFoundProblem(task_id)
''')
        def service = getBean(context, "python.TaskService")
        def problemType = context.classLoader.loadClass("python.TaskNotFoundProblem")

        when:
        service.find(3)

        then:
        def e = thrown(AbstractProblemLike)
        problemType.isInstance(e)
        e.type == "https://example.org/not-found"
        e.title == "Not found"
        e.status == 404
        e.detail == "Task '3' not found"
        e.message == "Not found: Task '3' not found"

        when: "the problem is created from Java"
        AbstractProblemLike created = problemType.getConstructor(int).newInstance(7)

        then:
        created.status == 404
        created.detail == "Task '7' not found"

        cleanup:
        context?.close()
    }

    void "the Python cause of a raised exception becomes the Java cause"() {
        given:
        def context = buildContext('''
from java.lang import IllegalStateException, RuntimeException
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable


class WrappedException(RuntimeException):

    def __init__(self, message: str):
        super().__init__(message)


@Singleton
class WrappingService:

    @Executable
    def run(self) -> None:
        try:
            raise IllegalStateException("inner")
        except IllegalStateException as e:
            raise WrappedException("outer") from e
''')
        def service = getBean(context, "python.WrappingService")

        when:
        service.run()

        then:
        def e = thrown(RuntimeException)
        e.message == "outer"
        e.cause instanceof IllegalStateException
        e.cause.message == "inner"

        cleanup:
        context?.close()
    }

    void "a Python exception raised by a controller is handled with its message"() {
        given:
        def context = buildContext('''
import java
from java.lang import RuntimeException
from jakarta.inject import Singleton
from micronaut.http import HttpRequest, HttpResponse
from micronaut.http.annotation import Controller, Get, Produces

ExceptionHandler = java.type("io.micronaut.http.server.exceptions.ExceptionHandler")


class OutOfStockException(RuntimeException):

    def __init__(self, isbn: str):
        super().__init__("No stock for " + isbn)


@Controller("/books")
class BookController:

    @Produces("text/plain")
    @Get("/stock/{isbn}")
    def stock(self, isbn: str) -> int:
        raise OutOfStockException(isbn)


@Produces
@Singleton
class OutOfStockExceptionHandler(ExceptionHandler[OutOfStockException, HttpResponse]):

    def handle(self, request: HttpRequest, e: OutOfStockException) -> HttpResponse:
        return HttpResponse.badRequest(e.getMessage())
''', true)
        def embeddedServer = context.getBean(EmbeddedServer)
        embeddedServer.start()
        def client = context.createBean(HttpClient, embeddedServer.URL)

        when:
        client.toBlocking().exchange(HttpRequest.GET("/books/stock/1234"), String)

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.BAD_REQUEST
        e.response.getBody(String).get() == "No stock for 1234"

        cleanup:
        client.close()
        context?.close()
    }
}
