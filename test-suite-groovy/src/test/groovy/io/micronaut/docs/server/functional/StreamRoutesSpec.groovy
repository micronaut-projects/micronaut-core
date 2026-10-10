package io.micronaut.docs.server.functional

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class StreamRoutesSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ["spec.name": "StreamRoutesSpec"])
    @Shared @AutoCleanup HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    void "the routes stream their responses"() {
        expect:
        client.toBlocking().retrieve(HttpRequest.GET("/countdown/3")) == "id: 3\ndata: 3\n\nid: 2\ndata: 2\n\nid: 1\ndata: 1\n\n"
        client.toBlocking().retrieve(HttpRequest.GET("/ticks")) == "data: tick 1\n\ndata: tick 2\n\ndata: tick 3\n\n"
        client.toBlocking().retrieve(HttpRequest.POST("/words", "a b").contentType(MediaType.TEXT_PLAIN_TYPE)) == "data: a\n\ndata: b\n\n"
        client.toBlocking().retrieve(HttpRequest.GET("/numbers")) == "[1,2,3]"
        // an error before the first event is answered by the error routes
        client.toBlocking().retrieve(HttpRequest.GET("/orders/1/updates")) == "data: order 1 shipped\n\n"
        // after it, as an event
        client.toBlocking().retrieve(HttpRequest.GET("/jobs/1")) == "data: started\n\ndata: done\n\n"
        client.toBlocking().retrieve(HttpRequest.GET("/jobs/2")) == "data: started\n\nevent: error\ndata: the job failed\n\n"
        // a reconnecting client resumes after the last event it received
        client.toBlocking().retrieve(HttpRequest.GET("/feed")) == "id: 1\nretry: 5000\ndata: item 1\n\nid: 2\ndata: item 2\n\nid: 3\ndata: item 3\n\n"
        client.toBlocking().retrieve(HttpRequest.GET("/feed").header("Last-Event-ID", "1")) == "id: 2\nretry: 5000\ndata: item 2\n\nid: 3\ndata: item 3\n\n"

        when:
        HttpResponse<String> notified = client.toBlocking().exchange(message("notify"), String)

        then:
        notified.status == HttpStatus.ACCEPTED
        notified.headers.get("Session-Id") == "s-1"
        client.toBlocking().retrieve(message("ping")) == '{"result":"pong"}'
        client.toBlocking().retrieve(message("hello")) == "data: received hello\n\n"
        // a client that accepts only JSON
        client.toBlocking().retrieve(HttpRequest.POST("/messages", "ping")
            .contentType(MediaType.TEXT_PLAIN_TYPE).accept(MediaType.APPLICATION_JSON_TYPE)) == '{"result":"pong"}'
    }

    void "an error before the first event is answered by the error routes"() {
        when:
        client.toBlocking().retrieve(HttpRequest.GET("/orders/2/updates"))

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.NOT_FOUND
    }

    private static MutableHttpRequest<String> message(String message) {
        HttpRequest.POST("/messages", message)
            .contentType(MediaType.TEXT_PLAIN_TYPE)
            .accept(MediaType.APPLICATION_JSON_TYPE, MediaType.TEXT_EVENT_STREAM_TYPE)
    }
}
