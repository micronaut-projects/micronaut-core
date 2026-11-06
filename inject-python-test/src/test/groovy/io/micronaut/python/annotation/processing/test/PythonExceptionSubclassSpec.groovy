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

    void "a constructor calling the super constructor with different arguments falls back to the message constructor"() {
        given:
        def context = buildContext('''
from java.lang import RuntimeException
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from micronaut.python.annotation.processing.test import AbstractProblemLike


class MaybeDetailed(RuntimeException):

    def __init__(self, detail=None):
        if detail:
            super().__init__(f"detail: {detail}")
        else:
            super().__init__()

        def helper():
            super().__init__("never called")


class MaybeProblem(AbstractProblemLike):

    def __init__(self, detail=None):
        if detail:
            super().__init__("t", "title", 404, detail)
        else:
            super().__init__()


@Singleton
class MaybeService:

    @Executable
    def run(self, detail: str) -> None:
        raise MaybeDetailed(detail)

    @Executable
    def problem(self, detail: str) -> None:
        raise MaybeProblem(detail)
''')
        def service = getBean(context, "python.MaybeService")
        def detailedType = context.classLoader.loadClass("python.MaybeDetailed")
        def problemType = context.classLoader.loadClass("python.MaybeProblem")

        when:
        service.run("d")

        then:
        def detailed = thrown(RuntimeException)
        detailedType.isInstance(detailed)
        detailed.message == "detail: d"

        when:
        service.run(null)

        then:
        def plain = thrown(RuntimeException)
        detailedType.isInstance(plain)
        plain.message == null

        when: "the base has no message constructor: its no-argument constructor is called"
        service.problem("d")

        then:
        def problem = thrown(AbstractProblemLike)
        problemType.isInstance(problem)
        problem.status == 400

        when:
        service.problem(null)

        then:
        def noDetail = thrown(AbstractProblemLike)
        problemType.isInstance(noDetail)
        noDetail.status == 400

        cleanup:
        context?.close()
    }

    void "a constructor passing its arguments through to the super constructor uses the message constructor"() {
        given:
        def context = buildContext('''
from java.lang import RuntimeException
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable


class PassThroughException(RuntimeException):

    def __init__(self, *args):
        super().__init__(*args)


@Singleton
class PassThroughService:

    @Executable
    def run(self) -> None:
        raise PassThroughException("boom")

    @Executable
    def silent(self) -> None:
        raise PassThroughException()
''')
        def service = getBean(context, "python.PassThroughService")
        def exceptionType = context.classLoader.loadClass("python.PassThroughException")

        when:
        service.run()

        then:
        def e = thrown(RuntimeException)
        exceptionType.isInstance(e)
        e.message == "boom"

        when:
        service.silent()

        then:
        def silent = thrown(RuntimeException)
        exceptionType.isInstance(silent)
        silent.message == null

        cleanup:
        context?.close()
    }

    void "a None argument for a primitive parameter of the Java super constructor is reported"() {
        given:
        def context = buildContext('''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from micronaut.python.annotation.processing.test import AbstractProblemLike


class StatusProblem(AbstractProblemLike):

    def __init__(self, status: int | None):
        super().__init__("t", "title", status, "detail")


@Singleton
class StatusService:

    @Executable
    def run(self, status: int | None) -> None:
        raise StatusProblem(status)
''')
        def service = getBean(context, "python.StatusService")
        def problemType = context.classLoader.loadClass("python.StatusProblem")

        when:
        service.run(404)

        then:
        def problem = thrown(AbstractProblemLike)
        problemType.isInstance(problem)
        problem.status == 404

        when:
        service.run(null)

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("python.StatusProblem")
        e.cause instanceof IllegalArgumentException
        e.cause.message.contains("primitive type [int]")

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
        accessors = [name for name in dir(e) if name in ("getMessage", "getCause")]
        return HttpResponse.badRequest(e.getMessage() + " " + str(sorted(accessors)))
''', true)
        def embeddedServer = context.getBean(EmbeddedServer)
        embeddedServer.start()
        def client = context.createBean(HttpClient, embeddedServer.URL)

        when:
        client.toBlocking().exchange(HttpRequest.GET("/books/stock/1234"), String)

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.BAD_REQUEST
        e.response.getBody(String).get() == "No stock for 1234 ['getCause', 'getMessage']"

        cleanup:
        client.close()
        context?.close()
    }
}
