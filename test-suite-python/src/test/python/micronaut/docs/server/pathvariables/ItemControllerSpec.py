from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http import HttpRequest, HttpStatus
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.http.client.exceptions import HttpClientResponseException
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

String = java.type("java.lang.String")


@Property(name="spec.name", value="ItemControllerSpec")
@MicronautTest
class ItemControllerSpec:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def reads_the_variables_of_the_route(self):
        assert self.get("/items/5") == "Item 5, page 1"
        assert self.get("/items/5/3") == "Item 5, page 3"

    @Test
    def reads_the_values_of_a_list_variable(self):
        assert self.get("/tags/red,green") == "Tags [red, green]"
        assert self.get("/sum/1,2,3") == "Sum 6"

    @Test
    def a_value_that_does_not_convert_is_a_bad_request(self):
        assert self.status("/items/abc") == HttpStatus.BAD_REQUEST

    @Test
    def a_filter_reads_the_variables_of_the_route(self):
        assert self.status("/items/7") == HttpStatus.GONE

    def get(self, uri: str) -> str:
        return self.client.toBlocking().retrieve(HttpRequest.GET(uri), String)

    def status(self, uri: str):
        try:
            self.get(uri)
        except HttpClientResponseException as e:
            return e.getStatus()
        raise AssertionError("expected an error response from " + uri)
