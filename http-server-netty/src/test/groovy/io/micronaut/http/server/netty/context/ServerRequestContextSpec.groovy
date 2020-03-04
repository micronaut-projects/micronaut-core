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
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.annotation.Error
import io.micronaut.http.context.ServerRequestContext
import io.micronaut.http.server.exceptions.ExceptionHandler
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
import javax.inject.Singleton
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
        "method"        | '/test-context/method'
        "rxjava"        | '/test-context/rxjava'
        "reactor"       | '/test-context/reactor'
        "thread"        | '/test-context/thread'
        "error"         | '/test-context/error'
        "handlerError"  | '/test-context/handler-error'
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

        @Get("/rxjava")
        Single<String> rxjava() {
            Single.fromCallable({ ->
                def request = ServerRequestContext.currentRequest().orElseThrow { -> new RuntimeException("no request") }
                request.uri
            }).subscribeOn(Schedulers.computation())
        }

        @Get("/reactor")
        Mono<String> reactor() {
            Mono.fromCallable({ ->
                def request = ServerRequestContext.currentRequest().orElseThrow { -> new RuntimeException("no request") }
                request.uri
            }).subscribeOn(reactor.core.scheduler.Schedulers.elastic())
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
