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

Map = java.type("java.util.Map")


@Property(name="spec.name", value="ValidatedRoutesTest")
@MicronautTest
class ValidatedRoutesTest:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def the_bean_method_validates_the_body(self):
        created = self.client.toBlocking().exchange(HttpRequest.POST("/products", Map.of("name", "lamp")))
        assert created.getStatus() == HttpStatus.CREATED
        try:
            self.client.toBlocking().exchange(HttpRequest.POST("/products", Map.of("name", "")))
            assert False
        except HttpClientResponseException as e:
            assert e.getStatus() == HttpStatus.BAD_REQUEST
