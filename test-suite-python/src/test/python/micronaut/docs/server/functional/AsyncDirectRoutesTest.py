from typing import Annotated

from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http import HttpRequest, HttpStatus
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.http.client.exceptions import HttpClientResponseException
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test


@Property(name="spec.name", value="AsyncDirectRoutesTest")
@MicronautTest
class AsyncDirectRoutesTest:
    client: Annotated[HttpClient, Inject, Client("/")]

    def _status(self, request) -> HttpStatus:
        try:
            self.client.toBlocking().retrieve(request)
        except HttpClientResponseException as e:
            return e.getStatus()
        raise AssertionError("the request did not fail")

    @Test
    def a_direct_route_runs_on_an_executor_or_completes_later(self):
        http = self.client.toBlocking()
        assert http.retrieve(HttpRequest.GET("/reports/2026-q1")) == "Q1: 1200 orders"
        assert http.retrieve(HttpRequest.GET("/quotes/MNT")) == "42.00"
        # declined: no ordinary route answers them
        assert self._status(HttpRequest.GET("/reports/2025-q4")) == HttpStatus.NOT_FOUND
        assert self._status(HttpRequest.GET("/quotes/XYZ")) == HttpStatus.NOT_FOUND
