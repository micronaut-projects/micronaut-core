package io.micronaut.python.annotation.processing.test

import io.micronaut.context.ApplicationContext
import io.micronaut.context.python.PythonStatic
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.python.compiler.PyronautCompiler
import io.micronaut.python.processing.staticcompile.StaticCompilationDecision
import io.micronaut.python.processing.staticcompile.StaticCompilationMode
import io.micronaut.runtime.server.EmbeddedServer

/**
 * The routes of a module are compiled into the module's generated class: a pooled route runs as
 * Java without leasing a Python context, calling the injected beans of the module through their
 * generated classes.
 */
class StaticCompilationScriptSpec extends AbstractPythonTypeElementSpec {
    List<StaticCompilationDecision> decisions = []

    @Override
    protected void configureCompiler(PyronautCompiler.Builder compilerBuilder) {
        compilerBuilder.staticCompilation(StaticCompilationMode.ALL)
            .options(["-A${StaticCompilationMode.TRACE_OPTION}=true".toString()])
            .staticCompilationDecisionCallback { decisions << it }
    }

    void "the routes of a pooled module run as Java through the injected beans of the module"() {
        given:
        PythonStatic.resetEntries()
        ApplicationContext context = buildContext('''
from typing import Annotated
from jakarta.inject import Inject, Singleton
from micronaut.http.annotation import Get

@Singleton
class Greeter:
    def greet(self, name: str) -> str:
        return "Hello " + name

    def _quietly(self, name: str) -> str:
        return name.lower()

greeter: Annotated[Greeter, Inject]

@Get("/routes/hello/{name}")
def hello(name: str) -> str:
    return greeter.greet(name) + "!"

@Get("/routes/quiet/{name}")
def quiet(name: str) -> str:
    return greeter._quietly(name)

def plain(name: str) -> str:
    return name
''', true)
        def server = context.getBean(EmbeddedServer)
        server.start()
        def client = context.createBean(HttpClient, server.URL)

        expect:
        decisions.find { it.qualifiedName() == 'hello' }.outcome() == StaticCompilationDecision.Outcome.COMPILED
        decisions.find { it.qualifiedName() == 'Greeter.greet' }.outcome() == StaticCompilationDecision.Outcome.COMPILED
        decisions.find { it.qualifiedName() == 'quiet' }.outcome() == StaticCompilationDecision.Outcome.SKIPPED
        decisions.find { it.qualifiedName() == 'quiet' }.reasons()*.rule() == ['pooled-module']
        decisions.find { it.qualifiedName() == 'plain' }.outcome() == StaticCompilationDecision.Outcome.COMPILED  // a static method of the generated class, reading no injected bean

        when:
        def hello = client.toBlocking().retrieve("/routes/hello/Ann")
        def quiet = client.toBlocking().retrieve("/routes/quiet/Ann")

        then: "the compiled route and the compiled method of the injected bean ran as Java; the skipped route ran as Python"
        hello == "Hello Ann!"
        quiet == "ann"
        PythonStatic.entries('python.Script#hello') == 1
        PythonStatic.entries('python.Greeter#greet') == 1

        cleanup:
        client?.close()
        context?.close()
    }

    void "a pooled route raises a python exception class as java, which its exception handler handles"() {
        given:
        PythonStatic.resetEntries()
        ApplicationContext context = buildContext('''
from jakarta.inject import Singleton
from micronaut.http import HttpRequest, HttpResponse
from micronaut.http.annotation import Get, Produces
from micronaut.http.server.exceptions import ExceptionHandler


class Missing(Exception):
    pass


@Singleton
@Produces("text/plain")
class MissingHandler(ExceptionHandler[Missing, HttpResponse]):
    def handle(self, request: HttpRequest, exception: Missing) -> HttpResponse:
        return HttpResponse.ok(f"missing {exception}")


@Get("/routes/find/{name}")
def find(name: str) -> str:
    if name == "x":
        raise Missing(name)
    return name
''', true)
        def server = context.getBean(EmbeddedServer)
        server.start()
        def client = context.createBean(HttpClient, server.URL)

        expect:
        decisions.find { it.qualifiedName() == 'find' }.outcome() == StaticCompilationDecision.Outcome.COMPILED

        when:
        def found = client.toBlocking().retrieve("/routes/find/a")
        def missing = client.toBlocking().retrieve("/routes/find/x")

        then: "the compiled route ran as Java and threw the generated class, which the handler declared for the Python class matched"
        found == "a"
        // the exception is created lazily, with the message of the Python exception
        missing == "missing x"
        PythonStatic.entries('python.Script#find') == 2

        cleanup:
        client?.close()
        context?.close()
    }

    static final String CONTROLLER_MODULE = '''
from typing import Annotated
from jakarta.inject import Inject, Singleton
from micronaut.http.annotation import Controller, Get

Controller("/ctl")
{scope}

@Singleton
class Greeter:
    def greet(self, name: str) -> str:
        return "Hello " + name

    def _quietly(self, name: str) -> str:
        return name.lower()

greeter: Annotated[Greeter, Inject]

@Get("/hello/{name}")
def hello(name: str) -> str:
    return greeter.greet(name) + "!"

@Get("/quiet/{name}")
def quiet(name: str) -> str:
    return greeter._quietly(name)
'''

    void "a controller module declaring no scope is pooled: a route reaching a python object stays in python"() {
        given: "@Controller is no scope: the module is pooled, as one without a module-level annotation is"
        PythonStatic.resetEntries()
        ApplicationContext context = buildContext(CONTROLLER_MODULE.replace('{scope}', ''), true)
        def server = context.getBean(EmbeddedServer)
        server.start()
        def client = context.createBean(HttpClient, server.URL)

        expect:
        decisions.find { it.qualifiedName() == 'hello' }.outcome() == StaticCompilationDecision.Outcome.COMPILED
        decisions.find { it.qualifiedName() == 'quiet' }.outcome() == StaticCompilationDecision.Outcome.SKIPPED
        decisions.find { it.qualifiedName() == 'quiet' }.reasons()*.rule() == ['pooled-module']

        when:
        def hello = client.toBlocking().retrieve("/ctl/hello/Ann")
        def quiet = client.toBlocking().retrieve("/ctl/quiet/Ann")

        then:
        hello == "Hello Ann!"
        quiet == "ann"
        PythonStatic.entries('python.Script#hello') == 1
        PythonStatic.entries('python.Script#quiet') == 0

        cleanup:
        client?.close()
        context?.close()
    }

    void "a controller module declaring a scope is not pooled: a route reaching a python object is compiled"() {
        given:
        PythonStatic.resetEntries()
        ApplicationContext context = buildContext(CONTROLLER_MODULE.replace('{scope}', 'Singleton()'), true)
        def server = context.getBean(EmbeddedServer)
        server.start()
        def client = context.createBean(HttpClient, server.URL)

        expect:
        decisions.find { it.qualifiedName() == 'quiet' }.outcome() == StaticCompilationDecision.Outcome.COMPILED

        when:
        def quiet = client.toBlocking().retrieve("/ctl/quiet/Ann")

        then:
        quiet == "ann"
        PythonStatic.entries('python.Script#quiet') == 1

        cleanup:
        client?.close()
        context?.close()
    }

    void "an advised module route is not compiled: its validation and its interceptors run"() {
        given:
        PythonStatic.resetEntries()
        ApplicationContext context = buildContext('''
from typing import Annotated
from jakarta.inject import Singleton
from jakarta.validation.constraints import NotBlank
from micronaut.aop import Around, InterceptorBean, MethodInvocationContext
from micronaut.http.annotation import Get
import java

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")

@Around
def Logged(target):
    return target

@Singleton
@InterceptorBean(Logged)
class LoggingInterceptor(MethodInterceptor):
    def intercept(self, context: MethodInvocationContext):
        return "logged " + str(context.proceed())

@Get("/advised/checked/{name}")
def checked(name: Annotated[str, NotBlank]) -> str:
    return name + "!"

@Get("/advised/logged/{name}")
@Logged
def logged(name: str) -> str:
    return name + "!"
''', true)
        def server = context.getBean(EmbeddedServer)
        server.start()
        def client = context.createBean(HttpClient, server.URL)

        expect: "the generated class of a module runs no compiled advice, so the functions stay in python"
        ['checked', 'logged'].every { name ->
            def decision = decisions.find { it.qualifiedName() == name }
            decision.outcome() == StaticCompilationDecision.Outcome.NOT_CANDIDATE && decision.reasons()*.rule() == ['intercepted-method']
        }

        when:
        def checked = client.toBlocking().retrieve("/advised/checked/Ann")
        def logged = client.toBlocking().retrieve("/advised/logged/Ann")

        then:
        checked == "Ann!"
        logged == "logged Ann!"

        when: "the constraint is violated"
        client.toBlocking().retrieve("/advised/checked/%20")

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.BAD_REQUEST

        cleanup:
        client?.close()
        context?.close()
    }
}
