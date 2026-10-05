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
import io.micronaut.inject.ast.ClassElement
import io.micronaut.runtime.server.EmbeddedServer

class PythonNativeDomainExceptionSpec extends AbstractPythonTypeElementSpec {

    void "native Python #base domain exception reaches its typed HTTP handler"(String base) {
        given:
        def context = buildContext("""
from jakarta.inject import Singleton
from micronaut.http import HttpRequest, HttpResponse
from micronaut.http.annotation import Controller, Get, Produces
from micronaut.http.server.exceptions import ExceptionHandler


class OutOfStockError(${base}):
    pass


@Singleton
@Produces("text/plain")
class OutOfStockErrorHandler(ExceptionHandler[OutOfStockError, HttpResponse]):
    def handle(self, request: HttpRequest, exception: OutOfStockError) -> HttpResponse:
        return HttpResponse.ok(0)


@Controller("/books")
class BookController:
    @Get("/stock/{isbn}")
    @Produces("text/plain")
    def stock(self, isbn: str) -> int:
        raise OutOfStockError()
""", true)
        def server = context.getBean(EmbeddedServer).start()
        def client = context.createBean(HttpClient, server.URL)

        when:
        def response = client.toBlocking().exchange(HttpRequest.GET("/books/stock/1234"), String)

        then:
        response.status == HttpStatus.OK
        response.body() == "0"

        cleanup:
        client?.close()
        context?.close()

        where:
        base << ["Exception", "RuntimeError", "ValueError"]
    }

    void "resolved native base #base preserves its executable Python ancestry"(String imports, String base) {
        given:
        def context = buildContext("""
${imports}
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable

class DomainError(${base}):
    pass

@Singleton
class Service:
    @Executable
    def run(self) -> None:
        raise DomainError("native")

    @Executable
    def has_native_base(self, error: DomainError) -> bool:
        return isinstance(error, ${base})
""")
        def service = getBean(context, "python.Service")

        when:
        service.run()

        then:
        def error = thrown(RuntimeException)
        context.classLoader.loadClass("python.DomainError").isInstance(error)
        error.message == "native"
        service.has_native_base(error)

        cleanup:
        context?.close()

        where:
        imports                                      | base
        "import builtins"                            | "builtins.RuntimeError"
        "import builtins as native"                  | "native.ValueError"
        "from builtins import RuntimeError as Native" | "Native"
        "from builtins import OSError"               | "OSError"
    }

    void "indirect native exceptions retain arbitrary args message cause and Python identity"() {
        given:
        def context = buildContext('''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable

class StockError(RuntimeError):
    def __init__(self, code: int, detail: str):
        super().__init__(code, detail, "native")

    def __str__(self) -> str:
        return f"{self.args[0]}: {self.args[1]}"

class OutOfStockError(StockError):
    pass

class CauseError(ValueError):
    pass

@Singleton
class Service:
    @Executable
    def run(self) -> None:
        self.cause = CauseError("inner")
        self.error = OutOfStockError(7, "empty")
        raise self.error from self.cause

    @Executable
    def same(self, error: OutOfStockError) -> bool:
        return (error.__cause__ is self.cause
                and isinstance(error, RuntimeError)
                and error.args == (7, "empty", "native"))

    @Executable
    def saved_error(self) -> OutOfStockError:
        return self.error
''')
        def service = getBean(context, "python.Service")

        when:
        service.run()

        then:
        def error = thrown(RuntimeException)
        context.classLoader.loadClass("python.OutOfStockError").isInstance(error)
        error.message == "7: empty"
        error.cause instanceof RuntimeException
        context.classLoader.loadClass("python.CauseError").isInstance(error.cause)
        error.cause.message == "inner"
        service.same(error)
        service.saved_error().asPolyglotValue() == error.asPolyglotValue()
        error.asPolyglotValue().getMember("__cause__") == error.cause.asPolyglotValue()

        cleanup:
        context?.close()
    }

    void "local classes shadow builtin exception names"() {
        expect:
        buildClassElement('''
class RuntimeError:
    pass

class DomainError(RuntimeError):
    pass
''', "DomainError") { ClassElement element ->
            assert element.superType.get().name == "python.RuntimeError"
            assert !element.isAssignable(Throwable)
            element
        }
    }

    void "BaseException-only #base control flow is not mapped to Throwable"(String base) {
        expect:
        buildClassElement("""
class ControlFlow(${base}):
    pass
""", "ControlFlow") { ClassElement element ->
            assert !element.isAssignable(Throwable)
            element
        }

        where:
        base << ["BaseException", "KeyboardInterrupt", "SystemExit"]
    }
}
