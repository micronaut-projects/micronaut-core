package io.micronaut.http.client.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.ClientFilter
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.ResponseFilter
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.netty.channel.EventLoopGroup
import io.netty.util.concurrent.EventExecutor
import org.junit.jupiter.api.Assertions
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import spock.lang.Specification

import java.util.concurrent.CountDownLatch

class StickyEventLoopSpec extends Specification {
    def 'connection reuse is sticky by default'() {
        given:
        def numThreads = 10
        def numClients = 15
        def ctx = ApplicationContext.run([
                'spec.name': 'StickyEventLoopSpec',
                'micronaut.http.client.event-loop-group': 'test-loop',
                'micronaut.netty.event-loops.test-loop.num-threads': numThreads
        ])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = ctx.createBean(HttpClient, server.URI)

        createConcurrentConnections(numClients, ctx, client)
        checkAllThreads(ctx, client)

        cleanup:
        client.close()
        server.stop()
        ctx.close()
    }

    def 'in enforced mode, we dont need initial clients'(String mode) {
        given:
        def numThreads = 10
        def ctx = ApplicationContext.run([
                'spec.name': 'StickyEventLoopSpec',
                'micronaut.http.client.event-loop-group': 'test-loop',
                'micronaut.http.client.pool.connection-locality': mode,
                'micronaut.netty.event-loops.test-loop.num-threads': numThreads
        ])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = ctx.createBean(HttpClient, server.URI)

        checkAllThreads(ctx, client)

        cleanup:
        client.close()
        server.stop()
        ctx.close()

        where:
        mode << ['enforced-if-same-group', 'enforced-always']
    }

    def 'in enforced-always mode, creating an outside request is forbidden'() {
        given:
        def numThreads = 10
        def ctx = ApplicationContext.run([
                'spec.name': 'StickyEventLoopSpec',
                'micronaut.http.client.event-loop-group': 'test-loop',
                'micronaut.http.client.pool.connection-locality': 'enforced-always',
                'micronaut.netty.event-loops.test-loop.num-threads': numThreads
        ])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = ctx.createBean(HttpClient, server.URI)

        when:
        client.toBlocking().retrieve("/sticky/simple")
        then:
        thrown HttpClientException

        cleanup:
        client.close()
        server.stop()
        ctx.close()
    }

    private static void checkAllThreads(ApplicationContext ctx, HttpClient client) {
        def filter = ctx.getBean(LoopDetectingFilter)
        def group = ctx.getBean(EventLoopGroup, Qualifiers.byName("test-loop"))
        for (EventExecutor loop : group) {
            Mono.defer { Mono.from(client.retrieve("/sticky/simple")) }
                    .doOnNext {Assertions.assertTrue(loop.inEventLoop(filter.thread)) }
                    .subscribeOn(Schedulers.fromExecutor(loop))
                    .block()
        }
    }

    private static void createConcurrentConnections(int numClients, ApplicationContext ctx, HttpClient client) {
        ctx.getBean(MyController).latch = new CountDownLatch(numClients)
        Flux.range(0, numClients)
                .flatMap(i -> client.retrieve('/sticky/concurrent'), Integer.MAX_VALUE)
                .blockLast()
    }

    @Controller("/sticky")
    @Requires(property = "spec.name", value = "StickyEventLoopSpec")
    static class MyController {
        CountDownLatch latch

        @Get("/simple")
        def simple() {
            return "foo"
        }

        @Get("/concurrent")
        @ExecuteOn(TaskExecutors.BLOCKING)
        def concurrent() {
            latch.countDown()
            latch.await()
            return "foo"
        }
    }

    @ClientFilter("/sticky/simple")
    @Requires(property = "spec.name", value = "StickyEventLoopSpec")
    static class LoopDetectingFilter {
        Thread thread

        @ResponseFilter
        void observe() {
            thread = Thread.currentThread()
        }
    }
}
