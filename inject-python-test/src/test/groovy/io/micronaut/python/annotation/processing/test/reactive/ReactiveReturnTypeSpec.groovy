package io.micronaut.python.annotation.processing.test.reactive

import io.micronaut.context.python.PythonContextRuntime
import io.micronaut.email.AsyncEmailSender
import io.micronaut.email.DefaultAsyncEmailSender
import io.micronaut.http.client.HttpClient
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

class ReactiveReturnTypeSpec extends AbstractPythonTypeElementSpec {

    void "python coroutine implementing Publisher interface emits scalar to injected Python caller"() {
        given:
        def context = buildContext('''
from jakarta.inject import Singleton
from micronaut.python.annotation.processing.test.reactive import PublisherFinder

@Singleton
class AsyncFinder(PublisherFinder):
    async def find(self, id: str, callback=None) -> str:
        return id

@Singleton
class AsyncCaller:
    def __init__(self, finder: PublisherFinder):
        self.finder = finder

    async def call(self) -> str:
        return await self.finder.find("from-python")
''', true)

        when:
        PublisherFinder finder = context.getBean(PublisherFinder)
        def caller = getBean(context, 'python.AsyncCaller')

        then:
        Flux.from(finder.find("from-java")).blockFirst() == "from-java"
        caller.call().toCompletableFuture().get() == "from-python"

        cleanup:
        context?.close()
    }

    void "reuse-context HTTP controller awaits DefaultAsyncEmailSender delegation"() {
        given:
        def context = buildContext('''
from jakarta.inject import Named, Singleton
from io.micronaut.email import AsyncEmailSender, AsyncTransactionalEmailSender, Email
from micronaut.http.annotation import Controller, Get

class AcceptedResponse:
    def getStatusCode(self) -> int:
        return 202

@Singleton
@Named("sendgrid")
class EmailSenderReplacement(AsyncTransactionalEmailSender):
    def getName(self) -> str:
        return "sendgrid"

    async def sendAsync(self, email: Email, email_request=None) -> AcceptedResponse:
        return AcceptedResponse()

@Controller("/email-async")
class EmailController:
    def __init__(self, sender: AsyncEmailSender):
        self.sender = sender

    @Get
    async def send(self) -> str:
        email = (
            Email.builder()
            .from_("sender@example.com")
            .to("test@example.com")
            .subject("test")
            .body("body")
        )
        response = await self.sender.sendAsync(email)
        return str(response.getStatusCode())

''', true)

        PythonContextRuntime.setReuseContext(true)
        assert context.getBean(AsyncEmailSender) instanceof DefaultAsyncEmailSender
        def server = context.getBean(EmbeddedServer)
        server.start()
        def client = context.createBean(HttpClient, server.URL)

        when:
        def result
        try {
            result = client.toBlocking().retrieve("/email-async")
        } catch (io.micronaut.http.client.exceptions.HttpClientResponseException error) {
            result = error.response.getBody(String).orElse(error.message)
        }

        then:
        result == "202"

        cleanup:
        client?.close()
        context?.close()
        PythonContextRuntime.setReuseContext(false)
    }

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

    void "unannotated python implementation of reactive interface methods uses the Java declaration"() {
        given:
        def context = buildContext('''
from jakarta.inject import Singleton
from micronaut.python.annotation.processing.test.reactive import ReactiveFinder
from reactor.core.publisher import Flux, Mono

@Singleton
class UnannotatedFinder(ReactiveFinder):
    def find(self, id):
        return Flux.just(id)

    def findAll(self):
        return Mono.just("a")

    def findFuture(self, id):
        return Mono.just(id)

    def findStage(self, id):
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

    void "unannotated python implementation of future interface methods returning futures"() {
        given:
        def context = buildContext('''
from jakarta.inject import Singleton
from micronaut.python.annotation.processing.test.reactive import ReactiveFinder
from reactor.core.publisher import Flux, Mono

@Singleton
class UnannotatedFutureFinder(ReactiveFinder):
    def find(self, id):
        return Mono.just(id)

    def findAll(self):
        return Flux.just("a", "b")

    def findFuture(self, id):
        return Mono.just(id).toFuture()

    def findStage(self, id):
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

    async def findWithCallback(self, id: str, callback=None) -> str:
        return id

    async def findGenericWithCallback(self, id: str, callback=None) -> str:
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
        finder.findWithCallback("c", null).block() == "c"
        finder.findGenericWithCallback("g", null).block() == "g"

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
