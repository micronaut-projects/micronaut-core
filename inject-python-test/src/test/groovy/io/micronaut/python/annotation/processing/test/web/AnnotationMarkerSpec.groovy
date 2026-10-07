package io.micronaut.python.annotation.processing.test.web

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Header
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.MethodElement
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * Annotations applied as markers: a call of an annotation as a parameter default or a module attribute value
 * ({@code content_type: str = Header()}), joined with {@code &}, and module-level aliases of {@code Annotated[...]}.
 * Type checkers accept both, because the type stays in the annotation.
 */
class AnnotationMarkerSpec extends AbstractPythonTypeElementSpec {

    void "test a marker default annotates the parameter"() {
        expect:
        buildClassElement('''
from jakarta.inject import Singleton
from jakarta.validation.constraints import NotBlank, Size
from micronaut.http.annotation import Controller, Get, Header

@Singleton
@Controller("/markers")
class Markers:
    @Get("/{name}")
    def find(self, name: str, trace: str = Header("X-Trace"), q: str = NotBlank() & Size(max=50)) -> str:
        return name
''') { ClassElement element ->
            MethodElement find = element.getEnclosedElement(ElementQuery.ALL_METHODS.named("find")).get()
            def trace = find.parameters.find { it.name == "trace" }
            assert trace.stringValue(Header).get() == "X-Trace"
            assert trace.type.name == String.name
            def q = find.parameters.find { it.name == "q" }
            assert q.hasAnnotation(NotBlank)
            assert q.intValue(Size, "max").asInt == 50
            element
        }
    }

    void "test facade markers annotate the parameter"() {
        expect:
        buildClassElement('''
from facadetest import http, validation

@http.Controller("/markers")
class Markers:
    @http.Get("/{name}")
    def find(self, name: str, trace: str = http.Header("X-Trace"), q: str = validation.NotBlank() & validation.Size(max=50)) -> str:
        return name
''') { ClassElement element ->
            MethodElement find = element.getEnclosedElement(ElementQuery.ALL_METHODS.named("find")).get()
            assert find.parameters.find { it.name == "trace" }.stringValue(Header).get() == "X-Trace"
            def q = find.parameters.find { it.name == "q" }
            assert q.hasAnnotation(NotBlank)
            assert q.intValue(Size, "max").asInt == 50
            element
        }
    }

    void "test aliases of Annotated carry their metadata"() {
        expect:
        buildClassElement('''
from dataclasses import dataclass
from typing import Annotated, TypeAlias
from jakarta.validation.constraints import NotBlank, Size
from micronaut.core.annotation import Introspected

type PersonName = Annotated[str, NotBlank, Size(min=1, max=30)]
Nickname: TypeAlias = Annotated[str, Size(max=10)]
Title = Annotated[str, NotBlank]

@Introspected
@dataclass
class Person:
    name: PersonName
    nickname: Nickname
    title: Title
''') { ClassElement element ->
            def name = element.getBeanProperties().find { it.name == "name" }
            assert name.type.name == String.name
            assert name.hasAnnotation(NotBlank)
            assert name.intValue(Size, "max").asInt == 30
            assert element.getBeanProperties().find { it.name == "nickname" }.intValue(Size, "max").asInt == 10
            assert element.getBeanProperties().find { it.name == "title" }.hasAnnotation(NotBlank)
            element
        }
    }

    void "test an annotation used as a default without a call is reported"() {
        when:
        buildClassElement('''
from micronaut.http.annotation import Controller, Get, Header

@Controller("/markers")
class Markers:
    @Get("/")
    def find(self, trace: str = Header) -> str:
        return trace
''') { ClassElement element -> element }

        then:
        def e = thrown(RuntimeException)
        def messages = []
        for (Throwable t = e; t != null; t = t.cause) {
            messages << String.valueOf(t.message)
        }
        messages.join("\n").contains("The annotation [Header] is a default value without being called: write [Header()]")
    }

    void "test an injected parameter a bridge cannot pass is reported: #description"() {
        when:
        buildContext(source, true)

        then:
        def e = thrown(RuntimeException)
        def messages = []
        for (Throwable t = e; t != null; t = t.cause) {
            messages << String.valueOf(t.message)
        }
        messages.join("\n").contains(message)

        where:
        description                      | source                                                    | message
        "a function declaring *args"     | """
from jakarta.inject import Inject
from micronaut.context import ApplicationContext
from micronaut.http.annotation import Get


@Get("/module/varargs")
def varargs(ctx: ApplicationContext = Inject(), *args: str) -> str:
    return str(ctx)
"""                                                                                                         | "The parameter [ctx] of [varargs] is injected with Inject(), which a function that declares *args does not support"
        "a static method of a class"     | """
from jakarta.inject import Inject
from micronaut.context import ApplicationContext
from micronaut.http.annotation import Controller, Get


@Controller("/markers")
class Markers:
    @staticmethod
    @Get("/static")
    def static_route(ctx: ApplicationContext = Inject()) -> str:
        return str(ctx)
"""                                                                                                         | "The parameter [ctx] of [static_route] is injected with Inject(), which a static or class method does not support"
    }

    void "test a parameter marked Inject() receives a bean, not a request value"() {
        given:
        def context = buildContext('''
from jakarta.inject import Inject
from micronaut.context import ApplicationContext
from micronaut.http.annotation import Controller, Get


@Get("/module/injected")
def module_injected(ctx: ApplicationContext = Inject()) -> str:
    return str(ctx.isRunning())


@Controller("/markers")
class Markers:
    @Get("/injected/{name}")
    def injected(self, name: str, ctx: ApplicationContext = Inject()) -> str:
        return name + ":" + str(ctx.isRunning())
''', true)
        def server = context.getBean(EmbeddedServer)
        server.start()
        def client = context.createBean(HttpClient, server.URL)

        expect:
        client.toBlocking().retrieve("/module/injected") == "True"
        client.toBlocking().retrieve("/markers/injected/fred") == "fred:True"

        cleanup:
        client?.close()
        context?.close()
    }

    void "test a parameter marked Inject() receives a bean on a context-pooled controller"() {
        given:
        def context = buildContext('''
from jakarta.inject import Inject
from micronaut.context import ApplicationContext
from micronaut.context.python.scope import ContextPooled
from micronaut.http.annotation import Controller, Get


@ContextPooled
@Controller("/pooled")
class Pooled:
    @Get("/{name}")
    def injected(self, name: str, ctx: ApplicationContext = Inject()) -> str:
        return name + ":" + str(ctx.isRunning())
''', true, ["micronaut.python.pool.size": 4])
        def server = context.getBean(EmbeddedServer)
        server.start()
        def client = context.createBean(HttpClient, server.URL)

        expect: "every per-context instance receives the bean the generated class holds"
        (1..8).collect { client.toBlocking().retrieve("/pooled/fred" + it) } == (1..8).collect { "fred" + it + ":True" }

        cleanup:
        client?.close()
        context?.close()
    }

    void "test markers at run time: request binding, module injection and direct calls"() {
        given:
        def context = buildContext('''
from jakarta.inject import Inject, Singleton
from jakarta.validation.constraints import NotBlank, Size
from micronaut.http.annotation import Controller, Get, Header
from micronaut.runtime.server import EmbeddedServer

embedded_server: EmbeddedServer = Inject()


@Get("/module/port")
def port() -> str:
    # a module with route functions is a controller bean, whose creation injects the module attributes
    return str(embedded_server.getPort())


@Controller("/markers")
class Markers:
    @Get("/trace")
    def trace(self, trace: str = Header("X-Trace")) -> str:
        return trace


@Singleton
class Direct:
    def call(self) -> str:
        # a direct Python call that omits a marked argument receives the marker, as with FastAPI
        return repr(Markers().trace()) + ";" + repr(NotBlank() & Size(max=5))
''', true)
        def server = context.getBean(EmbeddedServer)
        server.start()
        def client = context.createBean(HttpClient, server.URL)

        expect:
        client.toBlocking().retrieve(HttpRequest.GET("/markers/trace").header("X-Trace", "abc")) == "abc"
        client.toBlocking().retrieve("/module/port") == String.valueOf(server.port)
        context.getBean(context.classLoader.loadClass("python.Direct")).call() == "<Header marker>;<NotBlank & Size marker>"

        when: "the marked header is required, as an unannotated parameter would be"
        client.toBlocking().retrieve("/markers/trace")

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.BAD_REQUEST

        cleanup:
        client?.close()
        context?.close()
    }
}
