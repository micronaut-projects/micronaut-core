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
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.http.scope.RequestScope
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Inject
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.stream.IntStream

class CorruptedServerRequestContextSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(
            EmbeddedServer,
            // limit number of threads to simulate thread sharing
            ['micronaut.executors.io.type': 'FIXED',
             'micronaut.executors.io.nThreads':'2',]
    )

    @Shared
    @AutoCleanup
    HttpClient httpClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.URL)

    @Shared
    @AutoCleanup("shutdown")
    ExecutorService executor = Executors.newFixedThreadPool(40)

    void "simple"() {
        given:
        def response = callEndpoint().block()

        expect:
        response == '[ok][first][second]'
    }

    void "parallel"() {
        given:
        def response = callEndpointParallel(parallel).collectList().block()
        def expected = asListMultiplied('[ok][first][second]', parallel)

        expect:
        response == expected

        where:
        parallel  | _
        2         | _
        4         | _
        7         | _
        11        | _
        15        | _
        21        | _
        40        | _
    }

    private Mono<String> callEndpoint() {
        return Mono.from(httpClient.retrieve(HttpRequest.GET("/corrupted-context/mono-http-response-flux"), String.class));
    }

    private Flux<String> callEndpointParallel(int parallel) {
        Flux.range(0, parallel)
                .publishOn(Schedulers.fromExecutor(executor))
                .flatMap {callEndpoint() }
    }

    private List<String> asListMultiplied(String param, int count) {
        IntStream.range(0, count)
            .mapToObj {param }
            .collect()
    }

    @RequestScope
    static class RequestScopedBean {
        Boolean value
    }

    @Controller('/corrupted-context')
    @Produces(MediaType.TEXT_PLAIN)
    static class TestContextController {

        @Inject
        @Client("/corrupted-context")
        HttpClient client

        @Inject
        RequestScopedBean requestScopedBean

        @Get("/mono-http-response-flux")
        Mono<HttpResponse<Flux<String>>> monoHttpResponseFlux() {
            Mono.from(client.exchange("/first", String.class))
                    .<HttpResponse<Flux<String>>>map(first -> {

                        requestScopedBean.setValue(true)

                        var flux = Mono.from(client.exchange("/second", String.class))
                                .flatMapMany(second -> {
                                    if (requestScopedBean.getValue() == null) {
                                        throw new IllegalStateException("!!CONTEXT IS LOST!!")
                                    }

                                    return Flux.just("[ok]", "[${first.body()}]", "[${second.body()}]")
                                })
                                .onErrorResume(Exception.class, e -> Mono.just("error: " + e.getMessage()))

                        return HttpResponse.ok(flux);
                    })
        }

        @Get("/first")
        Mono<String> first() {
            return Mono.just("first")
        }

        @Get("/second")
        Mono<String> second() {
            return Mono.just("second")
        }
    }
}
