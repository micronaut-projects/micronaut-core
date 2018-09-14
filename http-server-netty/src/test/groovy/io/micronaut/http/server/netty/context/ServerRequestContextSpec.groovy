package io.micronaut.http.server.netty.context

import io.micronaut.context.ApplicationContext
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.Client
import io.micronaut.http.context.ServerRequestContext
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import javax.inject.Inject
import javax.inject.Named
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

class ServerRequestContextSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)

    @Unroll
    void "test server request context is available for #method"() {
        given:
        TestClient testClient = embeddedServer.getApplicationContext().getBean(TestClient)

        expect:
        testClient."$method"() == uri

        where:
        method   | uri
        "method" | '/test-context/method'
        "rxjava" | '/test-context/rxjava'
        "reactor" | '/test-context/reactor'
        "thread" | '/test-context/thread'
    }

    @Client('/test-context')
    static interface TestClient {

        @Get("/method")
        String method()

        @Get("/rxjava")
        String rxjava()

        @Get("/reactor")
        String reactor()

        @Get("/thread")
        String thread()
    }

    @Controller('/test-context')
    @Produces(MediaType.TEXT_PLAIN)
    static class TestContextController {

        @Inject
        @Named(TaskExecutors.IO)
        ExecutorService executorService

        @Get("/method")
        String method() {
            def request = ServerRequestContext.currentRequest().orElseThrow { -> new RuntimeException("no request") }
            request.uri
        }

        @Get("/rxjava")
        Single<String> rxjava() {
            Single.fromCallable({ ->
                def request = ServerRequestContext.currentRequest().orElseThrow { -> new RuntimeException("no request")
                }
                request.uri
            }).subscribeOn(Schedulers.computation())
        }

        @Get("/reactor")
        Mono<String> reactor() {
            Mono.fromCallable({ ->
                def request = ServerRequestContext.currentRequest().orElseThrow { -> new RuntimeException("no request")
                }
                request.uri
            }).subscribeOn(reactor.core.scheduler.Schedulers.elastic())
        }

        @Get("/thread")
        String thread() {
            CompletableFuture future = new CompletableFuture()
            executorService.submit({ ->
                def request = ServerRequestContext.currentRequest().orElseThrow { -> new RuntimeException("no request")
                }
                future.complete request.uri
            })

            future.get()
        }
    }
}
