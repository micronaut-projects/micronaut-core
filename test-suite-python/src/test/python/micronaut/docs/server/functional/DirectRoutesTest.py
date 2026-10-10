from typing import Annotated

from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http import HttpHeaders, HttpRequest, HttpStatus
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.http.client.exceptions import HttpClientResponseException
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test


@Property(name="spec.name", value="DirectRoutesTest")
@MicronautTest
class DirectRoutesTest:
    client: Annotated[HttpClient, Inject, Client("/")]

    def _status(self, request) -> HttpStatus:
        try:
            self.client.toBlocking().retrieve(request)
        except HttpClientResponseException as e:
            return e.getStatus()
        raise AssertionError("the request did not fail")

    @Test
    def the_server_answers_before_the_request_is_created(self):
        http = self.client.toBlocking()
        assert http.retrieve(HttpRequest.GET("/probe/live")) == "UP"
        assert http.retrieve(HttpRequest.GET("/probe/db")) == "db UP"
        # a declined request continues to the ordinary route
        assert http.retrieve(HttpRequest.GET("/assets/logo.png")) == "cached logo.png"
        assert http.retrieve(HttpRequest.GET("/assets/readme.txt")) == "rendered readme.txt"
        assert http.retrieve(HttpRequest.GET("/robots.txt")) == "User-agent: *\nDisallow: /private/\n"
        # a conditional GET: the function reads the request
        assert http.retrieve(HttpRequest.GET("/version")) == "1.0.0"
        assert http.exchange(HttpRequest.GET("/version")).header(HttpHeaders.ETAG) == '"1.0.0"'
        not_modified = http.exchange(HttpRequest.GET("/version").header(HttpHeaders.IF_NONE_MATCH, '"1.0.0"'))
        assert not_modified.getStatus() == HttpStatus.NOT_MODIFIED
        assert self._status(HttpRequest.POST("/orders", "{}").header("User-Agent", "BadBot/1.0")) == HttpStatus.FORBIDDEN
        # no direct route matches: the request continues to the ordinary routes, here none
        assert self._status(HttpRequest.GET("/probe/queue")) == HttpStatus.NOT_FOUND
