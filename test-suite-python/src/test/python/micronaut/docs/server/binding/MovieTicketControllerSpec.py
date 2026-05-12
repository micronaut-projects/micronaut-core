from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

HashMap = java.type("java.util.HashMap")
HttpStatus = java.type("io.micronaut.http.HttpStatus")
UriTemplate = java.type("io.micronaut.http.uri.UriTemplate")


@MicronautTest
class MovieTicketControllerSpec:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def test_binding_bean(self):
        template = UriTemplate("/api/movie/ticket/terminator{?minPrice,maxPrice}")
        params = HashMap()
        params.put("minPrice", 5.0)
        params.put("maxPrice", 20.0)

        response = self.client.toBlocking().exchange(template.expand(params))

        assert response.status() == HttpStatus.OK
