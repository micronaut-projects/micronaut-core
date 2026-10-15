package io.micronaut.python.annotation.processing.test

import io.micronaut.context.annotation.Executable
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Status
import io.micronaut.http.client.annotation.Client
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.MethodElement
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * Curated Python modules (facades): the names a facade exports resolve to the Java types, static methods and
 * enum constants they stand for. The facades are those of {@code example.facades.TestFacadeImportMapper}.
 */
class FacadeImportSpec extends AbstractPythonTypeElementSpec {

    void "test names imported from a facade resolve to the Java annotations"() {
        expect:
        buildClassElement('''
from facadetest.http import Controller, Get, HttpResponse
from facadetest.inject import Singleton, Executable

@Singleton
@Controller("/hello")
class Hello:
    @Get("/{name}")
    @Executable
    def hello(self, name: str) -> HttpResponse[str]:
        return HttpResponse.ok(name)
''') { ClassElement element ->
            assert element.hasAnnotation(AnnotationUtil.SINGLETON)
            assert element.stringValue(Controller).get() == "/hello"
            MethodElement method = element.getEnclosedElement(io.micronaut.inject.ast.ElementQuery.ALL_METHODS.named("hello")).get()
            assert method.stringValue(Get).get() == "/{name}"
            assert method.hasAnnotation(Executable)
            assert method.returnType.name == HttpResponse.name
            element
        }
    }

    void "test a facade imported as a module resolves its members"() {
        expect:
        buildClassElement('''
from facadetest import http, inject

@inject.Singleton
@http.Controller("/hello")
class Hello:
    @http.Get("/{name}")
    def hello(self, name: str) -> http.HttpResponse[str]:
        return http.ok(name)

    @http.Post("/")
    @http.Status(http.CREATED)
    def create(self, name: str) -> str:
        return name
''') { ClassElement element ->
            assert element.hasAnnotation(AnnotationUtil.SINGLETON)
            assert element.stringValue(Controller).get() == "/hello"
            MethodElement hello = method(element, "hello")
            assert hello.stringValue(Get).get() == "/{name}"
            assert hello.returnType.name == HttpResponse.name
            MethodElement create = method(element, "create")
            assert create.hasAnnotation(Post)
            assert create.enumValue(Status, HttpStatus).get() == HttpStatus.CREATED
            element
        }
    }

    void "test static field constants of a facade in annotation values"() {
        expect:
        buildClassElement('''
from facadetest import http
from facadetest.http import TEXT_PLAIN

@http.Controller("/hello")
class Hello:
    @http.Get(uri="/json", produces=http.APPLICATION_JSON)
    def json(self) -> str:
        return "{}"

    @http.Get(uri="/text", produces=TEXT_PLAIN)
    def text(self) -> str:
        return "text"
''') { ClassElement element ->
            assert method(element, "json").stringValues(Get, "produces") == ["application/json"] as String[]
            assert method(element, "text").stringValues(Get, "produces") == ["text/plain"] as String[]
            element
        }
    }

    void "test a facade imported with import as resolves its members"() {
        expect:
        buildClassElement('''
import facadetest.http as web

@web.Controller("/hello")
class Hello:
    @web.Get
    def hello(self) -> str:
        return "hello"
''') { ClassElement element ->
            assert element.stringValue(Controller).get() == "/hello"
            assert method(element, "hello").hasAnnotation(Get)
            element
        }
    }

    void "test a facade referenced by its full name"() {
        expect:
        buildClassElement('''
import facadetest.http

@facadetest.http.Controller("/hello")
class Hello:
    @facadetest.http.Get("/")
    def hello(self) -> str:
        return "hello"
''') { ClassElement element ->
            assert element.stringValue(Controller).get() == "/hello"
            assert method(element, "hello").hasAnnotation(Get)
            element
        }
    }

    void "test a star import of a facade binds every member"() {
        expect:
        buildClassElement('''
from typing import Annotated
from facadetest.http import *
from facadetest.validation import *

@Controller("/hello")
class Hello:
    @Post("/")
    @Status(CREATED)
    def create(self, name: Annotated[str, NotBlank, Size(min=1, max=30)]) -> HttpResponse[str]:
        return created(name)
''') { ClassElement element ->
            assert element.stringValue(Controller).get() == "/hello"
            MethodElement create = method(element, "create")
            assert create.enumValue(Status, HttpStatus).get() == HttpStatus.CREATED
            def parameter = create.parameters[0]
            assert parameter.hasAnnotation(NotBlank)
            assert parameter.intValue(Size, "max").asInt == 30
            element
        }
    }

    void "test facade members in Annotated metadata"() {
        expect:
        buildClassElement('''
from typing import Annotated
from facadetest import http, validation

@http.Controller("/hello")
class Hello:
    @http.Get("/{name}")
    def hello(self, name: Annotated[str, validation.NotBlank, validation.Size(min=1, max=30)]) -> str:
        return name
''') { ClassElement element ->
            def parameter = method(element, "hello").parameters[0]
            assert parameter.hasAnnotation(NotBlank)
            assert parameter.intValue(Size, "min").asInt == 1
            assert parameter.intValue(Size, "max").asInt == 30
            element
        }
    }

    void "test a clash decided for the annotation"() {
        expect:
        buildClassElement('''
from facadetest import inject

@inject.Qualifier
class Marker:
    pass
''') { ClassElement element ->
            assert element.hasDeclaredAnnotation("jakarta.inject.Qualifier")
            element
        }
    }

    void "test a nested facade is a member of its parent"() {
        expect:
        buildClassElement('''
from facadetest import http

@http.client.Client("/remote")
class Remote:
    pass
''') { ClassElement element ->
            assert element.stringValue(Client).get() == "/remote"
            element
        }
    }

    void "test a facade import keeps the import of other names of its package"() {
        expect:
        buildClassElement('''
from facadetest import http
from typing import Optional

@http.Controller("/hello")
class Hello:
    @http.Get
    def hello(self) -> Optional[str]:
        return None
''') { ClassElement element ->
            assert element.hasAnnotation(Controller)
            element
        }
    }

    void "test a name the facade lacks is reported with suggestions"() {
        when:
        buildClassElement('''
from facadetest import http

@http.Controller("/hello")
class Hello:
    @http.Gett("/")
    def hello(self) -> str:
        return "hello"
''') { ClassElement element -> element }

        then:
        def e = thrown(RuntimeException)
        messageOf(e).contains("Python module [facadetest.http] has no member [Gett]")
        messageOf(e).contains("Did you mean [Get]")
    }

    void "test importing a name the facade lacks is reported"() {
        when:
        buildClassElement('''
from facadetest.http import Controler

@Controler("/hello")
class Hello:
    pass
''') { ClassElement element -> element }

        then:
        def e = thrown(RuntimeException)
        messageOf(e).contains("Cannot import [Controler] from [facadetest.http]")
        messageOf(e).contains("[Controller]")
    }

    void "test a facade whose sources are missing is reported"() {
        when:
        buildClassElement('''
from facadetest import missing

@missing.Thing
class Hello:
    pass
''') { ClassElement element -> element }

        then:
        def e = thrown(RuntimeException)
        messageOf(e).contains("io.micronaut.does.not.exist")
        messageOf(e).contains("io.micronaut.example:does-not-exist")
    }

    void "test a bare import of a facade used by its last segment is reported"() {
        when:
        buildClassElement('''
import facadetest.http

@http.Controller("/hello")
class Hello:
    pass
''') { ClassElement element -> element }

        then:
        def e = thrown(RuntimeException)
        messageOf(e).contains("[import facadetest.http] binds the name [facadetest], not [http]")
        messageOf(e).contains("from facadetest import http")
    }

    private static MethodElement method(ClassElement element, String name) {
        element.getEnclosedElement(io.micronaut.inject.ast.ElementQuery.ALL_METHODS.named(name)).get()
    }

    private static String messageOf(Throwable e) {
        def messages = []
        def current = e
        while (current != null) {
            messages << String.valueOf(current.message)
            current = current.cause
        }
        messages.join("\n")
    }
}
