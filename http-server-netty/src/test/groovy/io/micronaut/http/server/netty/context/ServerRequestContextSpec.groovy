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
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.*
import io.micronaut.http.client.annotation.Client
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
             'micronaut.executors.io.nThreads':'2',]
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

        @Get("/reactor")
        Mono<String> reactor() {
            Mono.fromCallable({ ->
                def request = ServerRequestContext.currentRequest().orElseThrow { -> new RuntimeException("no request") }
                request.uri
            }).subscribeOn(Schedulers.boundedElastic())
        }

        @Get("/reactor-context")
        Mono<String> reactorContext() {
            Mono.deferContextual({ ctx ->
                def request = (HttpRequest) ctx.get(ServerRequestContext.KEY)
                return Mono.just(request.uri)
            })
        }

        @Get("/reactor-context-stream")
        Flux<String> reactorContextStream() {
            Flux.deferContextual({ ctx ->
                def request = (HttpRequest) ctx.get(ServerRequestContext.KEY)
                return Mono.just(request.uri)
            })
        }


        @Get("/thread")
        String thread() {
            CompletableFuture future = new CompletableFuture()
            executorService.submit({ ->
                def request = ServerRequestContext.currentRequest().orElseThrow { -> new RuntimeException("no request") }
                future.complete request.uri
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
                def request = ServerRequestContext.currentRequest().orElseThrow { -> new RuntimeException("no request") }
                future.complete HttpResponse.ok(request.uri)
            })

            future.get()
        }
    }

    @Singleton
    static class TestExceptionHandler implements ExceptionHandler<TestExceptionHandlerException, HttpResponse<String>> {
        @Inject
        @Named(TaskExecutors.IO)
        ExecutorService executorService

        @Override
        HttpResponse<String> handle(HttpRequest r, TestExceptionHandlerException exception) {
            CompletableFuture future = new CompletableFuture()
            executorService.submit({ ->
                def request = ServerRequestContext.currentRequest().orElseThrow { -> new RuntimeException("no request") }
                future.complete HttpResponse.ok(request.uri).contentType(MediaType.TEXT_PLAIN_TYPE)
            })

            future.get()
        }
    }

    static class TestException extends RuntimeException {}
    static class TestExceptionHandlerException extends RuntimeException {}
}
