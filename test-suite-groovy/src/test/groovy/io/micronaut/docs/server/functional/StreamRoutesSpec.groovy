package io.micronaut.docs.server.functional

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
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

        when:
        HttpResponse<String> notified = client.toBlocking().exchange(message("notify"), String)

        then:
        notified.status == HttpStatus.ACCEPTED
        notified.headers.get("Session-Id") == "s-1"
        client.toBlocking().retrieve(message("ping")) == '{"result":"pong"}'
        client.toBlocking().retrieve(message("hello")) == "data: received hello\n\n"
    }

    private static MutableHttpRequest<String> message(String message) {
        HttpRequest.POST("/messages", message)
            .contentType(MediaType.TEXT_PLAIN_TYPE)
            .accept(MediaType.APPLICATION_JSON_TYPE, MediaType.TEXT_EVENT_STREAM_TYPE)
    }
}
