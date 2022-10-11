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
package io.micronaut.http.client.sse

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.sse.Event
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

class ServerSentEventStreamingSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': 'ServerSentEventStreamingSpec'])
    @Shared @AutoCleanup SseClient sseClient = embeddedServer.applicationContext.createBean(SseClient, embeddedServer.getURL())
    @Shared ProductClient productClient = embeddedServer.applicationContext.getBean(ProductClient)

    void "test consume SSE stream with SseClient"() {
        when:
        List<Event<Product>> results = Flux.from(sseClient.eventStream("/stream/sse/pojo/events", Product)).collectList().block()

        then:
        results[0].data.name == "Apple"
        results[1].data.name == "Orange"
        results[1].data.quantity == 2
        results[1].id == 'o1'
        results[2].data.name == "Banana"
        results[3].data.name == "Kiwi"

    }

    void "test consume SSE stream with @Client"() {
        when:
        List<Event<Product>> results = Flux.from(productClient.pojoEventStream()).collectList().block()

        then:
        results[0].data.name == "Apple"
        results[1].data.name == "Orange"
        results[1].data.quantity == 2
        results[1].id == 'o1'
        results[2].data.name == "Banana"
        results[3].data.name == "Kiwi"
    }

    void "test consume pojo SSE stream with @Client"() {
        when:
        List<Product> results = Flux.from(productClient.pojoStream()).collectList().block()

        then:
        results[0].name == "Apple"
        results[1].name == "Orange"
        results[1].quantity == 2
        results[2].name == "Banana"
        results[3].name == "Kiwi"
    }

    // this tests that read timeout is not applied, but instead idle timeout is applied
    void "test consume pojo delayed SSE stream with @Client"() {
        when:
        List<Product> results = Flux.from(productClient.delayedStream()).collectList().block()

        then:
        results[0].name == "Apple"
        results[1].name == "Orange"
        results[1].quantity == 2
        results[2].name == "Banana"
        results[3].name == "Kiwi"
    }

    static List<Event<Product>> dataSet() {
        [
                Event.of(new Product(name: "Apple")),
                Event.of(new Product(name: "Orange", quantity: 2))
                        .id('o1')
                        .comment("From Valencia"),
                Event.of(new Product(name: "Banana", quantity: 5)),
                Event.of(new Product(name: "Kiwi", quantity: 15))
                        .comment("Green")
                        .id('k1')

        ]
    }

    @Requires(property = 'spec.name', value = 'ServerSentEventStreamingSpec')
    @Client("/stream/sse")
    static interface ProductClient {

        @Get(value = '/pojo/events', processes = MediaType.TEXT_EVENT_STREAM)
        Publisher<Event<Product>> pojoEventStream()

        @Get(value = '/pojo/objects', processes = MediaType.TEXT_EVENT_STREAM)
        Publisher<Product> pojoStream()

        @Get(value = '/pojo/delayed', processes = MediaType.TEXT_EVENT_STREAM)
        Publisher<Product> delayedStream()
    }

    @Requires(property = 'spec.name', value = 'ServerSentEventStreamingSpec')
    @Controller("/stream/sse")
    @ExecuteOn(TaskExecutors.IO)
    static class SseController {


        @Get(value = '/pojo/events', produces = MediaType.TEXT_EVENT_STREAM)
        Publisher<Event<Product>> pojoEventStream() {
            return Flux.fromIterable(dataSet())
        }

        @Get(value = '/pojo/objects', produces = MediaType.TEXT_EVENT_STREAM)
        Publisher<Product> pojoStream() {
            return Flux.fromIterable(dataSet().collect { it.data })
        }

        @Get(value = '/pojo/delayed', produces = MediaType.TEXT_EVENT_STREAM)
        Publisher<Product> delayedStream() {
            return Flux.fromIterable(dataSet().collect { it.data })
                    .delayElements(Duration.of(5, ChronoUnit.SECONDS))
        }
    }

    @EqualsAndHashCode
    @ToString(includePackage = false)
    static class Product {
        String name
        int quantity = 1
    }
}
