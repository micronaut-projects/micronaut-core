package io.micronaut.python.annotation.processing.test

import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.context.python.PythonStatic
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.python.annotation.processing.test.flow.InMemoryBookRepository
import io.micronaut.python.compiler.PyronautCompiler
import io.micronaut.python.processing.staticcompile.StaticCompilationDecision
import io.micronaut.python.processing.staticcompile.StaticCompilationMode
import io.micronaut.runtime.server.EmbeddedServer

/**
 * A common flow runs as Java end to end: validated routes of a controller class and of a module
 * call a service bean, which calls a Java repository as Micronaut Data declares one.
 */
class StaticCompilationFlowSpec extends AbstractPythonTypeElementSpec {
    List<StaticCompilationDecision> decisions = []

    @Override
    protected void configureContext(ApplicationContextBuilder builder) {
        // the Java repository, as a Micronaut Data repository would be registered by its own definition
        builder.singletons(new InMemoryBookRepository())
    }

    @Override
    protected void configureCompiler(PyronautCompiler.Builder compilerBuilder) {
        compilerBuilder.staticCompilation(StaticCompilationMode.ALL)
            .options(["-A${StaticCompilationMode.TRACE_OPTION}=true".toString()])
            .staticCompilationDecisionCallback { decisions << it }
    }

    static final String FLOW = '''
from typing import Annotated
from jakarta.inject import Inject, Singleton
from jakarta.validation.constraints import Min, NotBlank
from micronaut.http.annotation import Controller, Get, Post
from io.micronaut.python.annotation.processing.test.flow import BookRepository


@Singleton
class Library:
    def __init__(self, repository: BookRepository):
        self.repository = repository

    def add(self, title: Annotated[str, NotBlank]) -> str:
        return self.repository.save(title.strip())

    def size(self) -> int:
        return self.repository.count()

    def shelf(self) -> str:
        titles = self.repository.titles()
        if len(titles) == 0:
            return "empty"
        return ", ".join(titles)

    def longest(self, limit: int) -> int:
        longest = 0
        for title, count in self.repository.pages().items():
            if count > longest and count <= limit:
                longest = count
        return longest


@Controller("/library")
class LibraryController:
    def __init__(self, library: Library):
        self.library = library

    @Post("/{title}")
    def add(self, title: Annotated[str, NotBlank]) -> str:
        return self.library.add(title)

    @Get("/size")
    def size(self) -> int:
        return self.library.size()

    @Get("/shelf")
    def shelf(self) -> str:
        return self.library.shelf()

    @Get("/longest/{limit}")
    def longest(self, limit: Annotated[int, Min(1)]) -> int:
        return self.library.longest(limit)


library: Annotated[Library, Inject]


@Get("/routes/size")
def route_size() -> int:
    return library.size()


@Post("/routes/{title}")
def route_add(title: Annotated[str, NotBlank]) -> str:
    return library.add(title)
'''

    void "a validated controller, a module route and a service calling a java repository run as java"() {
        given:
        PythonStatic.resetEntries()
        ApplicationContext context = buildContext(FLOW, true)
        def server = context.getBean(EmbeddedServer)
        server.start()
        def client = context.createBean(HttpClient, server.URL).toBlocking()

        expect: "every method of the flow is compiled"
        def compiled = decisions.findAll { it.outcome() == StaticCompilationDecision.Outcome.COMPILED }*.qualifiedName()
        def expected = ['Library.add', 'Library.size', 'Library.shelf', 'Library.longest',
                        'LibraryController.add', 'LibraryController.size', 'LibraryController.shelf', 'LibraryController.longest',
                        'route_size', 'route_add']
        def notCompiled = expected.collect { name -> decisions.find { it.qualifiedName() == name } }
            .findAll { it == null || it.outcome() != StaticCompilationDecision.Outcome.COMPILED }
            .collect { "${it?.qualifiedName()}: ${it?.reasons()*.rule()} ${it?.reasons()*.message()}".toString() }
        notCompiled == []
        compiled.containsAll(expected)

        when:
        def added = client.retrieve(io.micronaut.http.HttpRequest.POST("/library/Dune", ""))
        def routeAdded = client.retrieve(io.micronaut.http.HttpRequest.POST("/routes/Emma", ""))
        def size = client.retrieve("/library/size")
        def routeSize = client.retrieve("/routes/size")
        def shelf = client.retrieve("/library/shelf")
        def longest = client.retrieve("/library/longest/45")

        then: "the flow ran as Java through the controller, the module route, the service and the repository"
        added == 'Dune'
        routeAdded == 'Emma'
        size == '2'
        routeSize == '2'
        shelf == 'Dune, Emma'
        longest == '40'
        PythonStatic.entries('python.LibraryController#add') == 1
        PythonStatic.entries('python.LibraryController#size') == 1
        PythonStatic.entries('python.LibraryController#shelf') == 1
        PythonStatic.entries('python.LibraryController#longest') == 1
        PythonStatic.entries('python.Script#route_size') == 1
        PythonStatic.entries('python.Script#route_add') == 1
        PythonStatic.entries('python.Library#add') == 2
        PythonStatic.entries('python.Library#size') == 2
        PythonStatic.entries('python.Library#shelf') == 1
        PythonStatic.entries('python.Library#longest') == 1

        when: "a constraint of the controller is violated"
        client.retrieve(io.micronaut.http.HttpRequest.POST("/library/%20", ""))

        then: "the validation advice ran in Java before the body"
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.BAD_REQUEST
        PythonStatic.entries('python.LibraryController#add') == 1

        when: "a constraint of the service is violated through the module route"
        client.retrieve(io.micronaut.http.HttpRequest.POST("/routes/%20", ""))

        then:
        def e2 = thrown(HttpClientResponseException)
        e2.status == HttpStatus.BAD_REQUEST
        PythonStatic.entries('python.Library#add') == 2

        when: "a numeric constraint is violated"
        client.retrieve("/library/longest/0")

        then:
        def e3 = thrown(HttpClientResponseException)
        e3.status == HttpStatus.BAD_REQUEST

        cleanup:
        client?.close()
        context?.close()
    }
}
