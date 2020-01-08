package io.micronaut.http.client.sse

import io.micronaut.context.ApplicationContext
import io.micronaut.http.sse.Event
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.http.client.sse.ServerSentEventStreamingSpec.ProductClient
import io.micronaut.http.client.sse.ServerSentEventStreamingSpec.Product
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

@Issue("https://github.com/micronaut-projects/micronaut-core/issues/2586")
class ServerSentEventMultilineSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['jackson.serialization.indentOutput': true])
    @Shared @AutoCleanup RxSseClient sseClient = embeddedServer.applicationContext.createBean(RxSseClient, embeddedServer.getURL())
    @Shared ProductClient productClient = embeddedServer.applicationContext.getBean(ProductClient)


    void "test consume multiline SSE stream with RxSseClient"() {
        when:
        List<Event<Product>> results = sseClient.eventStream("/stream/sse/pojo/events", Product).toList().blockingGet()

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
        List<Product> results = productClient.pojoStream().toList().blockingGet()

        then:
        results[0].name == "Apple"
        results[1].name == "Orange"
        results[1].quantity == 2
        results[2].name == "Banana"
        results[3].name == "Kiwi"
    }
}
