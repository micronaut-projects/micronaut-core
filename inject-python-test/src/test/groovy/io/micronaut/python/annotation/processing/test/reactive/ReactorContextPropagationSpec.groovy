package io.micronaut.python.annotation.processing.test.reactive

import io.micronaut.core.async.propagation.ReactorPropagation
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.util.context.Context

/**
 * The Reactor context of the subscriber (a reactive transaction status, for instance) must reach the
 * publishers a Python method returns and the publishers created by the Python lambdas inside them.
 */
class ReactorContextPropagationSpec extends AbstractPythonTypeElementSpec {

    private static final String PYTHON_FINDER = '''
from jakarta.inject import Singleton
from micronaut.python.annotation.processing.test.reactive import ContextualFinder, ContextualService
from org.reactivestreams import Publisher
from reactor.core.publisher import Flux, Mono

@Singleton
class PythonContextualFinder(ContextualFinder):
    def __init__(self):
        self.service = ContextualService()

    def nested(self) -> Mono[str]:
        return self.service.value().flatMap(
            lambda outer: self.service.value().map(lambda inner: outer + "/" + inner))

    def nestedElement(self) -> Mono[str]:
        return self.service.element().flatMap(
            lambda outer: self.service.element().map(lambda inner: outer + "/" + inner))

    def nestedPublisher(self) -> Publisher[str]:
        return self.service.value().flatMap(
            lambda outer: self.service.value().map(lambda inner: outer + "/" + inner))

    def nestedMany(self) -> Flux[str]:
        return Flux.from_(self.service.value()).flatMap(
            lambda outer: self.service.value().map(lambda inner: outer + "/" + inner))
'''

    void "the subscriber's Reactor context reaches the publishers a Python method returns"() {
        given:
        def context = buildContext(PYTHON_FINDER, true)
        ContextualFinder finder = context.getBean(ContextualFinder)

        expect:
        finder.nested().contextWrite(Context.of("tx", "T1")).block() == "T1/T1"
        Mono.from(finder.nestedPublisher()).contextWrite(Context.of("tx", "T1")).block() == "T1/T1"
        finder.nestedMany().contextWrite(Context.of("tx", "T1")).collectList().block() == ["T1/T1"]
        finder.nested().block() == "none/none"

        cleanup:
        context?.close()
    }

    void "a propagated context element in the subscriber's Reactor context reaches the publishers a Python method returns"() {
        given:
        def context = buildContext(PYTHON_FINDER, true)
        ContextualFinder finder = context.getBean(ContextualFinder)

        expect:
        finder.nestedElement()
            .contextWrite(ctx -> ReactorPropagation.addContextElement(ctx, new TransactionElement("T1")))
            .block() == "T1/T1"
        finder.nestedElement().block() == "none/none"

        cleanup:
        context?.close()
    }

    void "the Reactor context reaches the publishers a Python coroutine awaits"() {
        given:
        def context = buildContext('''
from typing import Annotated
from jakarta.inject import Inject, Singleton
from micronaut.context.annotation import Bean, Factory
from micronaut.python.annotation.processing.test.reactive import ContextualFinder, ContextualService

@Factory
class ContextualServiceFactory:
    @Singleton
    @Bean
    def service(self) -> ContextualService:
        return ContextualService()

@Singleton
class AsyncContextualFinder(ContextualFinder):
    service: Annotated[ContextualService, Inject]

    async def nested(self) -> str:
        outer = await self.service.value()
        inner = await self.service.value()
        return outer + "/" + inner

    async def nestedElement(self) -> str:
        outer = await self.service.element()
        inner = await self.service.element()
        return outer + "/" + inner

    async def nestedPublisher(self) -> str:
        outer = await self.service.value()
        inner = await self.service.value()
        return outer + "/" + inner

    async def nestedMany(self) -> str:
        outer = await self.service.value()
        inner = await self.service.value()
        return outer + "/" + inner
''', true)
        ContextualFinder finder = context.getBean(ContextualFinder)

        expect:
        finder.nested().contextWrite(Context.of("tx", "T1")).block() == "T1/T1"
        finder.nestedElement()
            .contextWrite(ctx -> ReactorPropagation.addContextElement(ctx, new TransactionElement("T1")))
            .block() == "T1/T1"
        Mono.from(finder.nestedPublisher()).contextWrite(Context.of("tx", "T1")).block() == "T1/T1"
        finder.nestedMany().contextWrite(Context.of("tx", "T1")).collectList().block() == ["T1/T1"]
        finder.nested().block() == "none/none"

        cleanup:
        context?.close()
    }

    void "the Reactor context written by a Java operation reaches the publishers of the Python lambda it runs"() {
        given:
        def context = buildContext('''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from micronaut.python.annotation.processing.test.reactive import ContextualService
from reactor.core.publisher import Flux, Mono

@Singleton
class ProgrammaticTransactions:
    def __init__(self):
        self.service = ContextualService()

    @Executable
    def run(self) -> Flux[str]:
        return Flux.from_(self.service.withTransaction("T2", lambda tx:
            Flux.from_(self.service.value())
                .flatMap(lambda outer: self.service.element().map(lambda inner: tx + ":" + outer + "/" + inner))))
''', true)
        def definition = getBeanDefinition(context, 'python.ProgrammaticTransactions')
        def bean = getBean(context, 'python.ProgrammaticTransactions')

        when:
        def result = definition.getRequiredMethod("run").invoke(bean)

        then:
        result instanceof Flux
        result.collectList().block() == ["T2:T2/T2"]

        cleanup:
        context?.close()
    }

    void "the Reactor context reaches the publisher of an intercepted Python method called from Python"() {
        given:
        def context = buildContext('''
from jakarta.inject import Singleton
from java.lang import IllegalStateException
from micronaut.aop import Around, InterceptorBean, MethodInterceptor, MethodInvocationContext
from micronaut.context.annotation import Executable
from micronaut.python.annotation.processing.test.reactive import ContextualService
from reactor.core.publisher import Flux, Mono

@Around
def RequiresTransaction(func):
    return func

# a MANDATORY reactive transaction: the intercepted publisher fails unless the Reactor context of its subscriber carries one
@InterceptorBean(RequiresTransaction)
class RequiresTransactionInterceptor(MethodInterceptor):
    def intercept(self, context: MethodInvocationContext):
        publisher = context.proceed()
        return Flux.deferContextual(lambda ctx:
            Flux.from_(publisher) if ctx.hasKey("tx")
            else Flux.error(IllegalStateException("Expected an existing transaction, but none was found in the Reactive context")))

@Singleton
class BookRepository:
    def __init__(self):
        self.service = ContextualService()

    @RequiresTransaction
    def save(self, title: str) -> Mono[str]:
        return self.service.value().map(lambda tx: title + "@" + tx)

@Singleton
class BookService:
    def __init__(self, repository: BookRepository):
        self.repository = repository
        self.service = ContextualService()

    @Executable
    def store(self) -> Flux[str]:
        return Flux.from_(self.service.withTransaction("T3", lambda tx:
            Flux.from_(self.repository.save("first")).flatMap(lambda saved: self.repository.save(saved + "+second"))))

    @Executable
    def store_without_transaction(self) -> Mono[str]:
        return self.repository.save("first")
''', true)
        def definition = getBeanDefinition(context, 'python.BookService')
        def bean = getBean(context, 'python.BookService')

        when:
        def stored = definition.getRequiredMethod("store").invoke(bean)

        then:
        stored instanceof Flux
        stored.collectList().block() == ["first@T3+second@T3"]

        when:
        def outside = definition.getRequiredMethod("store_without_transaction").invoke(bean)
        outside.block()

        then:
        def e = thrown(IllegalStateException)
        e.message.startsWith("Expected an existing transaction")

        cleanup:
        context?.close()
    }
}
