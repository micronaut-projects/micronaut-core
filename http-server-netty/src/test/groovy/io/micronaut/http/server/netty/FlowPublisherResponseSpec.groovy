package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.FlowAdapters
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.Flow

class FlowPublisherResponseSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': 'FlowPublisherResponseSpec'])
    @Shared @AutoCleanup HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

    void "a Flow.Publisher body is streamed like a reactive streams publisher"() {
        expect:
        client.toBlocking().retrieve("/flow-publisher/items") == client.toBlocking().retrieve("/flow-publisher/rs-items")
        client.toBlocking().retrieve("/flow-publisher/thread").contains("eventLoop")
    }

    @Requires(property = 'spec.name', value = 'FlowPublisherResponseSpec')
    @Controller("/flow-publisher")
    static class FlowController {

        @Get("/items")
        Flow.Publisher<String> items() {
            FlowAdapters.toFlowPublisher(Flux.just("a", "b"))
        }

        @Get("/rs-items")
        Publisher<String> rsItems() {
            Flux.just("a", "b")
        }

        @Get(value = "/thread", produces = "text/plain")
        Flow.Publisher<String> thread() {
            FlowAdapters.toFlowPublisher(Flux.just(Thread.currentThread().name))
        }
    }
}
