from typing import Annotated

from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http import HttpRequest
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test


@Property(name="spec.name", value="HelloRoutesTest")
@MicronautTest
class HelloRoutesTest:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def the_handler_answers(self):
        assert self.client.toBlocking().retrieve(HttpRequest.GET("/hello/World")) == "Hello World"
