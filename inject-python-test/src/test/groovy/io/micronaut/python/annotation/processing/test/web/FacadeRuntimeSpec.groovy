package io.micronaut.python.annotation.processing.test.web

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec
import io.micronaut.runtime.server.EmbeddedServer

/**
 * Curated Python modules (facades) at run time: the compiler writes a module for every facade the sources
 * import, whose members resolve through the Java import finder. The facades are those of {@code example.facades.TestFacadeImportMapper}.
 */
class FacadeRuntimeSpec extends AbstractPythonTypeElementSpec {

    void "test a controller written against facades serves requests"() {
        given:
        def context = buildContext('''
from typing import Annotated
from facadetest import http, inject
from facadetest.http import NOT_FOUND, Controller, Get

@inject.Singleton
class Greeter:
    def greet(self, name: str) -> str:
        return f"Hello {name}"

@http.Controller("/facade")
class FacadeController:
    def __init__(self, greeter: Greeter):
        self.greeter = greeter

    @http.Get("/hello/{name}")
    def hello(self, name: str) -> http.HttpResponse[str]:
        return http.ok(self.greeter.greet(name))

    @http.Post("/items")
    @http.Status(http.CREATED)
    def create(self, name: Annotated[str, http.Body]) -> str:
        return name

    @Get("/missing")
    def missing(self) -> http.HttpResponse[str]:
        return http.status(NOT_FOUND)

    @http.Get(uri="/text", produces=http.TEXT_PLAIN)
    def text(self) -> str:
        return "plain"
''', true)
        def embeddedServer = context.getBean(EmbeddedServer)
        embeddedServer.start()
        def client = context.createBean(HttpClient, embeddedServer.URL)

        expect:
        client.toBlocking().retrieve("/facade/hello/John") == "Hello John"

        when:
        def created = client.toBlocking().exchange(HttpRequest.POST("/facade/items", '"widget"'), String)

        then:
        created.status == HttpStatus.CREATED
        created.body() == '"widget"'

        when:
        client.toBlocking().exchange("/facade/missing", String)

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.NOT_FOUND

        when:
        def text = client.toBlocking().exchange("/facade/text", String)

        then:
        text.contentType.get().toString() == "text/plain"
        text.body() == "plain"

        cleanup:
        client?.close()
        context?.close()
    }

    void "test a module importing facades runs with the line numbers of its source"() {
        given: "a module whose facade references the compile-time rewrite turns into Java imports at its top"
        def source = '''
from facadetest import http, inject
from facadetest.http import CREATED, ok


@inject.Singleton
class Lines:

    @http.Get("/lines")
    def decorated(self) -> http.HttpResponse[str]:
        return ok(str(CREATED))

    def fail(self) -> int:
        import traceback
        try:
            raise ValueError(http.CREATED)
        except ValueError as e:
            return traceback.extract_tb(e.__traceback__)[-1].lineno

    def first_line(self) -> int:
        return Lines.decorated.__code__.co_firstlineno
'''
        def context = buildContext(source, true)
        List<String> lines = source.readLines()

        when:
        def bean = context.getBean(context.classLoader.loadClass("python.Lines"))

        then: "a traceback names the line of the source, not of the rewritten module"
        bean.fail() == lines.findIndexOf { it.contains('raise ValueError(http.CREATED)') } + 1

        and: "a decorated function starts at its first decorator, as in the source"
        bean.first_line() == lines.findIndexOf { it.contains('@http.Get("/lines")') } + 1

        cleanup:
        context?.close()
    }

    void "test facades are modules whose members are the Java types, functions and constants"() {
        given:
        def context = buildContext('''
import facadetest
import facadetest.http.client
from facadetest import http
from facadetest.http import Get, CREATED
from facadetest.validation import *
from micronaut.http import HttpStatus
from micronaut.http.annotation import Get as JavaGet
from facadetest import inject

@inject.Singleton
class Probe:
    def report(self) -> str:
        try:
            getattr(http, "Gett")
            missing = "no error"
        except AttributeError as e:
            missing = str(e)
        entries = {
            "constant": http.CREATED == HttpStatus.CREATED and CREATED == HttpStatus.CREATED,
            "annotation_identity": http.Get is Get and Get is JavaGet,
            "annotation_name": http.Get.java_class_name,
            "static": http.ok("x").getStatus().getCode(),
            "star": NotBlank.java_class_name,
            "all": "ok" in http.__all__ and "client" in http.__all__ and "Get" in http.__all__,
            "dir": "HttpResponse" in dir(http),
            "nested": http.client is facadetest.http.client,
            "nested_static": http.client.GET("/x").getMethodName(),
            "namespace": facadetest.http is http,
            "doc": http.__doc__,
            "missing": missing,
        }
        return ";".join(f"{key}={value}" for key, value in entries.items())
''', true)

        when:
        def probe = context.getBean(context.classLoader.loadClass("python.Probe"))
        Map<String, String> report = probe.report().split(';').collectEntries { it.split('=', 2) as List }

        then:
        report.constant == "True"
        report.annotation_identity == "True"
        report.annotation_name == "io.micronaut.http.annotation.Get"
        report.static == "200"
        report.star == "jakarta.validation.constraints.NotBlank"
        report.all == "True"
        report.dir == "True"
        report.nested == "True"
        report.nested_static == "GET"
        report.namespace == "True"
        report.doc == "HTTP routing, requests and responses."
        report.missing.contains("module 'facadetest.http' has no attribute 'Gett'")

        cleanup:
        context?.close()
    }
}
