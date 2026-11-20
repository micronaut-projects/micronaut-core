package io.micronaut.docs.server.requestlog

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class RequestMetricsSpec extends Specification {

    void "the listener sees the route template"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name': 'RequestMetricsSpec',
                'micronaut.server.netty.request-log-enabled': true])
        HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)
        RequestMetrics metrics = server.applicationContext.getBean(RequestMetrics)

        expect:
        client.toBlocking().retrieve(HttpRequest.GET("/books/1")) == "book 1"
        new PollingConditions(timeout: 10).eventually {
            assert metrics.nanosByRoute.containsKey('/books/{id} 200')
        }

        cleanup:
        client.close()
        server.close()
    }

    @Requires(property = "spec.name", value = "RequestMetricsSpec")
    @Controller("/books")
    static class BookController {
        @Get("/{id}")
        String book(String id) {
            "book " + id
        }
    }
}
