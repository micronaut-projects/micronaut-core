from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http import HttpRequest, MediaType
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test


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
