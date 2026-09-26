package io.micronaut.python.annotation.processing.test

import io.micronaut.context.ApplicationContext
import io.micronaut.context.python.PythonStatic
import io.micronaut.http.client.HttpClient
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
        decisions.find { it.qualifiedName() == 'plain' }.outcome() == StaticCompilationDecision.Outcome.NOT_CANDIDATE

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
}
