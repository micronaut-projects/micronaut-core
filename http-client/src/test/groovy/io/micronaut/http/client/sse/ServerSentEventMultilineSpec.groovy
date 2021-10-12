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
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

@Issue("https://github.com/micronaut-projects/micronaut-core/issues/2586")
class ServerSentEventMultilineSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['jackson.serialization.indentOutput': true, 'spec.name': 'ServerSentEventMultilineSpec'])
    @Shared @AutoCleanup SseClient sseClient = embeddedServer.applicationContext.createBean(SseClient, embeddedServer.getURL())
    @Shared ProductClient productClient = embeddedServer.applicationContext.getBean(ProductClient)

    void "test consume multiline SSE stream with SseClient"() {
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

    void "test consume multiline pojo SSE stream with @Client"() {
        when:
        List<Product> results = Flux.from(productClient.pojoStream()).collectList().block()

        then:
        results[0].name == "Apple"
        results[1].name == "Orange"
        results[1].quantity == 2
        results[2].name == "Banana"
        results[3].name == "Kiwi"
    }

    @Requires(property = 'spec.name', value = 'ServerSentEventMultilineSpec')
    @Client("/stream/sse")
    static interface ProductClient {

        @Get(value = '/pojo/objects', processes = MediaType.TEXT_EVENT_STREAM)
        Publisher<Product> pojoStream()
    }

    @Requires(property = 'spec.name', value = 'ServerSentEventMultilineSpec')
    @Controller("/stream/sse")
    @ExecuteOn(TaskExecutors.IO)
    static class SseController {

        @Get(value = '/pojo/objects', produces = MediaType.TEXT_EVENT_STREAM)
        Publisher<Product> pojoStream() {
            return Flux.fromIterable(dataSet().collect { it.data })
        }

        @Get(value = '/pojo/events', produces = MediaType.TEXT_EVENT_STREAM)
        Publisher<Event<Product>> pojoEventStream() {
            return Flux.fromIterable(dataSet())
        }
    }

    @EqualsAndHashCode
    @ToString(includePackage = false)
    static class Product {
        String name
        int quantity = 1
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
}
