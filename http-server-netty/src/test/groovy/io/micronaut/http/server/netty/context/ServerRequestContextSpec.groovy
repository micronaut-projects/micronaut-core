/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.server.netty.context

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.context.ServerHttpRequestContext
import io.micronaut.http.context.ServerRequestContext
import io.micronaut.http.server.exceptions.ExceptionHandler
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

class ServerRequestContextSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(
            EmbeddedServer,
            // limit number of threads to simulate thread sharing
            ['micronaut.executors.io.type': 'FIXED',
             'micronaut.executors.io.nThreads':'2',
             'spec.name': 'ServerRequestContextSpec']
    )

    @Unroll
    void "test server request context is available for #method"() {
        given:
        TestClient testClient = embeddedServer.getApplicationContext().getBean(TestClient)

        expect:
        testClient."$method"() == uri

        where:
        method          | uri
        "error"         | '/test-context/error'
    }

    void "test the request is part of the reactor context"() {
        given:
        TestClient testClient = embeddedServer.getApplicationContext().getBean(TestClient)

        expect:
        testClient.reactorContext() == '/test-context/reactor-context'
        testClient.reactorContextStream() == '/test-context/reactor-context-stream'
    }

    @Requires(property = 'spec.name', value = 'ServerRequestContextSpec')
    @Client('/test-context')
    @Consumes(MediaType.TEXT_PLAIN)
    static interface TestClient {

        @Get("/method")
        String method()

        @Get("/reactor")
        String reactor()

        @Get("/reactor-context")
        String reactorContext()

        @Get("/reactor-context-stream")
        String reactorContextStream()

        @Get("/thread")
        String thread()

        @Get("/error")
        String error()

        @Get("/handler-error")
        String handlerError()
    }

    @Requires(property = 'spec.name', value = 'ServerRequestContextSpec')
    @Controller('/test-context')
    @Produces(MediaType.TEXT_PLAIN)
    static class TestContextController {

        @Inject
        @Named(TaskExecutors.IO)
        ExecutorService executorService

        @Get("/method")
        String method() {
            PropagatedContext.get().get(ServerHttpRequestContext)
            def request = ServerRequestContext.currentRequest().orElseThrow { -> new RuntimeException("no request") }
            request.uri.toString()
        }

        @Get("/reactor")
        Mono<String> reactor() {
            Mono.fromCallable({ ->
                def request = ServerRequestContext.currentRequest().orElseThrow { -> new RuntimeException("no request") }
                request.uri.toString()
            }).subscribeOn(Schedulers.boundedElastic())
        }

        @Get("/reactor-context")
        Mono<String> reactorContext() {
            Mono.deferContextual({ ctx ->
                PropagatedContext.get().get(ServerHttpRequestContext)
                def request = (HttpRequest) ctx.get(ServerRequestContext.KEY)
                return Mono.just(request.uri.toString())
            })
        }

        @Get("/reactor-context-stream")
        Flux<String> reactorContextStream() {
            Flux.deferContextual({ ctx ->
                PropagatedContext.get().get(ServerHttpRequestContext)
                def request = (HttpRequest) ctx.get(ServerRequestContext.KEY)
                return Mono.just(request.uri.toString())
            })
        }


        @Get("/thread")
        String thread() {
            CompletableFuture future = new CompletableFuture()
            executorService.submit({ ->
                PropagatedContext.get().get(ServerHttpRequestContext)
                def request = ServerRequestContext.currentRequest().orElseThrow { -> new RuntimeException("no request") }
                future.complete request.uri.toString()
            })

            future.get()
        }

        @Get("/error")
        String error() {
            throw new TestException()
        }

        @Get("/handler-error")
        String handlerError() {
            throw new TestExceptionHandlerException()
        }

        @Error(TestException)
        HttpResponse<String> errorHandler() {
            CompletableFuture future = new CompletableFuture()
            executorService.submit({ ->
                PropagatedContext.get().get(ServerHttpRequestContext)
                def request = ServerRequestContext.currentRequest().orElseThrow { -> new RuntimeException("no request") }
                future.complete HttpResponse.ok(request.uri.toString())
            })

            future.get()
        }
    }

    @Requires(property = 'spec.name', value = 'ServerRequestContextSpec')
    @Singleton
    static class TestExceptionHandler implements ExceptionHandler<TestExceptionHandlerException, HttpResponse<String>> {
        @Inject
        @Named(TaskExecutors.IO)
        ExecutorService executorService

        @Override
        HttpResponse<String> handle(HttpRequest r, TestExceptionHandlerException exception) {
            CompletableFuture future = new CompletableFuture()
            executorService.submit({ ->
                PropagatedContext.get().get(ServerHttpRequestContext)
                def request = ServerRequestContext.currentRequest().orElseThrow { -> new RuntimeException("no request") }
                future.complete HttpResponse.ok(request.uri).contentType(MediaType.TEXT_PLAIN_TYPE)
            })

            future.get()
        }
    }

    static class TestException extends RuntimeException {}
    static class TestExceptionHandlerException extends RuntimeException {}
}
