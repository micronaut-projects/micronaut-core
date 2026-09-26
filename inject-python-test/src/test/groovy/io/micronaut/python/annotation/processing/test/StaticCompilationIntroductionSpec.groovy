package io.micronaut.python.annotation.processing.test

import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.context.python.PythonStatic
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.python.annotation.processing.test.flow.InMemoryBookRepository
import io.micronaut.python.compiler.PyronautCompiler
import io.micronaut.python.processing.staticcompile.StaticCompilationDecision
import io.micronaut.python.processing.staticcompile.StaticCompilationMode
import io.micronaut.runtime.server.EmbeddedServer

/**
 * Introduction advice declared in Python, as Micronaut Data declares a repository, runs as Java end
 * to end: the controller calls the repository through its generated Java type, the introduction
 * proxy runs the chain in Java, and the interceptor's body is compiled.
 */
class StaticCompilationIntroductionSpec extends AbstractPythonTypeElementSpec {
    List<StaticCompilationDecision> decisions = []

    @Override
    protected void configureContext(ApplicationContextBuilder builder) {
        builder.singletons(new InMemoryBookRepository())
    }

    @Override
    protected void configureCompiler(PyronautCompiler.Builder compilerBuilder) {
        compilerBuilder.staticCompilation(StaticCompilationMode.ALL)
            .options(["-A${StaticCompilationMode.TRACE_OPTION}=true".toString()])
            .staticCompilationDecisionCallback { decisions << it }
    }

    static final String INTRODUCED = '''
from abc import ABC, abstractmethod
from typing import Annotated, Protocol
from jakarta.inject import Inject, Singleton
from micronaut.aop import InterceptorBean, Introduction, MethodInvocationContext
from micronaut.http.annotation import Controller, Get, Post
from io.micronaut.python.annotation.processing.test.flow import InMemoryBookRepository
import java

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")


@Introduction
def Repository(cls):
    return cls


@InterceptorBean(Repository)
@Singleton
class RepositoryInterceptor(MethodInterceptor):
    def __init__(self, store: InMemoryBookRepository):
        self.store = store

    def intercept(self, context: MethodInvocationContext) -> object:
        name = context.getMethodName()
        if name == "count":
            return self.store.count()
        title = str(context.getParameterValueMap().get("title"))
        if name == "save":
            return self.store.save(title)
        return "unknown " + name


@Repository
class BookProtocol(Protocol):
    def save(self, title: str) -> str:
        ...

    def count(self) -> int:
        ...


@Repository
class BookRepository(ABC):
    @abstractmethod
    def save(self, title: str) -> str:
        pass

    @abstractmethod
    def count(self) -> int:
        pass


@Repository
class BookShelf(ABC):
    """An abstract class with a concrete member: proxied at run time, its abstract methods bind the chain."""

    @abstractmethod
    def count(self) -> int:
        pass

    def label(self) -> str:
        return "shelf"


@Controller("/books")
class BooksController:
    def __init__(self, protocol: BookProtocol, repository: BookRepository, shelf: BookShelf):
        self.protocol = protocol
        self.repository = repository
        self.shelf = shelf

    @Get("/shelf/count")
    def count_through_shelf(self) -> int:
        return self.shelf.count()

    @Post("/protocol/{title}")
    def save_through_protocol(self, title: str) -> str:
        return self.protocol.save(title)

    @Get("/protocol/count")
    def count_through_protocol(self) -> int:
        return self.protocol.count()

    @Post("/abstract/{title}")
    def save_through_abstract(self, title: str) -> str:
        return self.repository.save(title)

    @Get("/abstract/count")
    def count_through_abstract(self) -> int:
        return self.repository.count()


repository: Annotated[BookRepository, Inject]


@Get("/routes/count")
def route_count() -> int:
    return repository.count()
'''

    void "introduction advice declared in python runs as java from the controller to the interceptor"() {
        given:
        PythonStatic.resetEntries()
        ApplicationContext context = buildContext(INTRODUCED, true)
        def server = context.getBean(EmbeddedServer)
        server.start()
        def client = context.createBean(HttpClient, server.URL).toBlocking()

        expect: "the interceptor and every caller of the introductions are compiled"
        def expected = ['RepositoryInterceptor.intercept', 'BooksController.save_through_protocol', 'BooksController.count_through_protocol',
                        'BooksController.save_through_abstract', 'BooksController.count_through_abstract', 'BooksController.count_through_shelf', 'route_count']
        def notCompiled = expected.collect { name -> decisions.find { it.qualifiedName() == name } }
            .findAll { it == null || it.outcome() != StaticCompilationDecision.Outcome.COMPILED }
            .collect { "${it?.qualifiedName()}: ${it?.reasons()*.rule()} ${it?.reasons()*.message()}".toString() }
        notCompiled == []

        when:
        def saved = client.retrieve(HttpRequest.POST("/books/protocol/Dune", ""))
        def savedAbstract = client.retrieve(HttpRequest.POST("/books/abstract/Emma", ""))
        def count = client.retrieve("/books/protocol/count")
        def countAbstract = client.retrieve("/books/abstract/count")
        def routeCount = client.retrieve("/routes/count")
        def shelfCount = client.retrieve("/books/shelf/count")

        then: "the introductions answered through the interceptor, whose body ran as Java each time"
        saved == 'Dune'
        savedAbstract == 'Emma'
        count == '2'
        countAbstract == '2'
        routeCount == '2'
        shelfCount == '2'
        PythonStatic.entries('python.RepositoryInterceptor#intercept') == 6
        PythonStatic.entries('python.BooksController#save_through_protocol') == 1
        PythonStatic.entries('python.BooksController#save_through_abstract') == 1
        PythonStatic.entries('python.BooksController#count_through_protocol') == 1
        PythonStatic.entries('python.BooksController#count_through_abstract') == 1
        PythonStatic.entries('python.Script#route_count') == 1
        PythonStatic.entries('python.BooksController#count_through_shelf') == 1

        and: "a protocol and a purely abstract class compile to interfaces, whose proxies Micronaut generates; an abstract class with concrete members is proxied at run time and binds the chain to its generated class"
        context.classLoader.loadClass('python.BookProtocol').isInterface()
        context.classLoader.loadClass('python.BookRepository').isInterface()
        !context.classLoader.loadClass('python.BookShelf').isInterface()
        context.getBean(context.classLoader.loadClass('python.BookShelf')) instanceof io.micronaut.context.python.aop.StaticAdviceTarget

        cleanup:
        client?.close()
        context?.close()
    }
}
