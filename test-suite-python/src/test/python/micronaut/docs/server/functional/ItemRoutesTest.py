from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http import HttpMethod, HttpRequest, HttpStatus, MediaType
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.http.client.exceptions import HttpClientResponseException
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

Integer = java.type("java.lang.Integer")
Map = java.type("java.util.Map")


@Property(name="spec.name", value="ItemRoutesTest")
@MicronautTest
class ItemRoutesTest:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def items_are_created_read_and_deleted(self):
        http = self.client.toBlocking()
        created = http.exchange(HttpRequest.POST("/items", Map.of("id", 0, "name", "pen")), Map)
        assert created.getStatus() == HttpStatus.CREATED
        id = created.body().get("id")
        assert http.retrieve(HttpRequest.GET(f"/items/{id}"), Map) == Map.of("id", id, "name", "pen")
        assert http.retrieve(HttpRequest.GET(f"/items/{id}/name")) == "pen"
        assert http.retrieve(HttpRequest.PATCH(f"/items/{id}/touch", "")) == f"touched {id} with PATCH"
        assert http.retrieve(HttpRequest.PUT(f"/items/{id}/touch", "")) == f"touched {id} with PUT"
        assert http.retrieve(HttpRequest.GET("/items/count"), Integer) >= 1
        assert http.exchange(HttpRequest.DELETE(f"/items/{id}")).getStatus() == HttpStatus.NO_CONTENT
        try:
            http.retrieve(HttpRequest.GET(f"/items/{id}"))
            assert False
        except HttpClientResponseException as e:
            assert e.getStatus() == HttpStatus.NOT_FOUND

    @Test
    def a_custom_method_and_a_nullable_body(self):
        http = self.client.toBlocking()
        assert http.retrieve(HttpRequest.create(HttpMethod.CUSTOM, "/items", "PROPFIND")) == "items: PROPFIND"
        assert http.exchange(HttpRequest.POST("/items/optional", None)
                             .contentType(MediaType.APPLICATION_JSON_TYPE)).getStatus() == HttpStatus.NO_CONTENT
        assert http.exchange(HttpRequest.POST("/items/optional", Map.of("id", 0, "name", "cup"))).getStatus() == HttpStatus.CREATED

    @Test
    def a_decoded_body_handled_asynchronously(self):
        created = self.client.toBlocking().exchange(HttpRequest.POST("/items/async", Map.of("id", 0, "name", "mug")), Map)
        assert created.getStatus() == HttpStatus.CREATED
        assert created.body().get("name") == "mug"

    @Test
    def an_implicit_head_route_and_a_method_not_allowed(self):
        http = self.client.toBlocking()
        item = http.retrieve(HttpRequest.POST("/items", Map.of("id", 0, "name", "book")), Map)
        id = item.get("id")
        assert http.exchange(HttpRequest.HEAD(f"/items/{id}")).getStatus() == HttpStatus.OK
        try:
            http.exchange(HttpRequest.PATCH(f"/items/{id}", ""))
            assert False
        except HttpClientResponseException as e:
            assert e.getStatus() == HttpStatus.METHOD_NOT_ALLOWED
