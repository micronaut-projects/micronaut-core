from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http import HttpRequest, HttpStatus, MediaType
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.http.client.exceptions import HttpClientResponseException
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

String = java.type("java.lang.String")


@Property(name="spec.name", value="StreamRoutesTest")
@MicronautTest
class StreamRoutesTest:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def the_routes_stream_their_responses(self):
        http = self.client.toBlocking()
        assert http.retrieve(HttpRequest.GET("/countdown/3")) == "id: 3\ndata: 3\n\nid: 2\ndata: 2\n\nid: 1\ndata: 1\n\n"
        assert http.retrieve(HttpRequest.GET("/ticks")) == "data: tick 1\n\ndata: tick 2\n\ndata: tick 3\n\n"
        assert http.retrieve(HttpRequest.POST("/words", "a b").contentType(MediaType.TEXT_PLAIN_TYPE)) == "data: a\n\ndata: b\n\n"
        assert http.retrieve(HttpRequest.GET("/numbers")) == "[1,2,3]"
        # an error before the first event is answered by the error routes
        assert http.retrieve(HttpRequest.GET("/orders/1/updates")) == "data: order 1 shipped\n\n"
        try:
            http.retrieve(HttpRequest.GET("/orders/2/updates"))
            raise AssertionError("expected a 404")
        except HttpClientResponseException as e:
            assert e.getStatus() == HttpStatus.NOT_FOUND
        # after it, as an event
        assert http.retrieve(HttpRequest.GET("/jobs/1")) == "data: started\n\ndata: done\n\n"
        assert http.retrieve(HttpRequest.GET("/jobs/2")) == "data: started\n\nevent: error\ndata: the job failed\n\n"
        # a reconnecting client resumes after the last event it received
        assert http.retrieve(HttpRequest.GET("/feed")) == "id: 1\nretry: 5000\ndata: item 1\n\nid: 2\ndata: item 2\n\nid: 3\ndata: item 3\n\n"
        assert http.retrieve(HttpRequest.GET("/feed").header("Last-Event-ID", "1")) == "id: 2\nretry: 5000\ndata: item 2\n\nid: 3\ndata: item 3\n\n"
        notified = http.exchange(message("notify"), String)
        assert notified.getStatus() == HttpStatus.ACCEPTED
        assert notified.getHeaders().get("Session-Id") == "s-1"
        assert http.retrieve(message("ping")) == '{"result":"pong"}'
        assert http.retrieve(message("hello")) == "data: received hello\n\n"
        # a client that accepts only JSON
        assert http.retrieve(HttpRequest.POST("/messages", "ping")
                             .contentType(MediaType.TEXT_PLAIN_TYPE)
                             .accept(MediaType.APPLICATION_JSON_TYPE)) == '{"result":"pong"}'


def message(message):
    return (HttpRequest.POST("/messages", message)
            .contentType(MediaType.TEXT_PLAIN_TYPE)
            .accept(MediaType.APPLICATION_JSON_TYPE, MediaType.TEXT_EVENT_STREAM_TYPE))
