package io.micronaut.python.annotation.processing.test.reactive

import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

class ReactiveReturnTypeSpec extends AbstractPythonTypeElementSpec {

    void "python implementation of Mono and Flux interface methods returning Reactor types"() {
        given:
        def context = buildContext('''
from java.util.concurrent import CompletableFuture, CompletionStage
from jakarta.inject import Singleton
from micronaut.python.annotation.processing.test.reactive import ReactiveFinder
from reactor.core.publisher import Flux, Mono

@Singleton
class ReactorFinder(ReactiveFinder):
    def find(self, id: str) -> Mono[str]:
        return Mono.just(id)

    def findAll(self) -> Flux[str]:
        return Flux.just("a", "b")

    def findFuture(self, id: str) -> CompletableFuture[str]:
        return Mono.just(id).toFuture()

    def findStage(self, id: str) -> CompletionStage[str]:
        return Mono.just(id).toFuture()
''', true)

        when:
        ReactiveFinder finder = context.getBean(ReactiveFinder)

        then:
        finder.find("x") instanceof Mono
        finder.find("x").block() == "x"
        finder.findAll() instanceof Flux
        finder.findAll().collectList().block() == ["a", "b"]
        finder.findFuture("f") instanceof CompletableFuture
        finder.findFuture("f").get() == "f"
        finder.findStage("s") instanceof CompletionStage
        finder.findStage("s").toCompletableFuture().get() == "s"

        cleanup:
        context?.close()
    }

    void "python implementation of Mono and Flux interface methods returning plain publishers"() {
        given:
        def context = buildContext('''
from jakarta.inject import Singleton
from micronaut.python.annotation.processing.test.reactive import ReactiveFinder
from org.reactivestreams import Publisher
from reactor.core.publisher import Flux, Mono

@Singleton
class PublisherFinder(ReactiveFinder):
    def find(self, id: str) -> Publisher[str]:
        return Flux.just(id)

    def findAll(self) -> Publisher[str]:
        return Mono.just("a")

    def findFuture(self, id: str) -> Publisher[str]:
        return Mono.just(id)

    def findStage(self, id: str) -> Publisher[str]:
        return Flux.just(id)
''', true)

        when:
        ReactiveFinder finder = context.getBean(ReactiveFinder)

        then:
        finder.find("x") instanceof Mono
        finder.find("x").block() == "x"
        finder.findAll() instanceof Flux
        finder.findAll().collectList().block() == ["a"]
        finder.findFuture("f") instanceof CompletableFuture
        finder.findFuture("f").get() == "f"
        finder.findStage("s") instanceof CompletionStage
        finder.findStage("s").toCompletableFuture().get() == "s"

        cleanup:
        context?.close()
    }

    void "python implementation of Mono interface methods with async coroutines"() {
        given:
        def context = buildContext('''
import asyncio
from jakarta.inject import Singleton
from micronaut.python.annotation.processing.test.reactive import ReactiveFinder
from reactor.core.publisher import Flux, Mono

@Singleton
class AsyncFinder(ReactiveFinder):
    async def find(self, id: str) -> str:
        await asyncio.sleep(0)
        return id

    def findAll(self) -> Flux[str]:
        return Flux.just("a")

    async def findFuture(self, id: str) -> str:
        return id

    async def findStage(self, id: str) -> str:
        return id
''', true)

        when:
        ReactiveFinder finder = context.getBean(ReactiveFinder)

        then:
        finder.findFuture("f") instanceof CompletableFuture
        finder.findFuture("f").get() == "f"
        finder.findStage("s") instanceof CompletionStage
        finder.findStage("s").toCompletableFuture().get() == "s"
        finder.find("x") instanceof Mono
        finder.find("x").block() == "x"

        cleanup:
        context?.close()
    }

    void "executable python methods declared as Mono and Flux return Reactor types"() {
        given:
        def context = buildContext('''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from org.reactivestreams import Publisher
from reactor.core.publisher import Flux, Mono

@Singleton
class ReactiveService:
    @Executable
    def single(self) -> Mono[str]:
        return Mono.just("one")

    @Executable
    def many(self) -> Flux[str]:
        return Flux.just("a", "b")

    @Executable
    def plain(self) -> Publisher[str]:
        return Mono.just("p")
''', true)

        when:
        def definition = getBeanDefinition(context, 'python.ReactiveService')
        def service = getBean(context, 'python.ReactiveService')
        def single = definition.getRequiredMethod("single").invoke(service)
        def many = definition.getRequiredMethod("many").invoke(service)
        def plain = definition.getRequiredMethod("plain").invoke(service)

        then:
        single instanceof Mono
        single.block() == "one"
        many instanceof Flux
        many.collectList().block() == ["a", "b"]
        Mono.from(plain).block() == "p"

        cleanup:
        context?.close()
    }
}
