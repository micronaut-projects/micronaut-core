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

from .ItemRepository import ItemRepository

Files = java.type("java.nio.file.Files")
List = java.type("java.util.List")
Map = java.type("java.util.Map")
Path = java.type("java.nio.file.Path")
System = java.type("java.lang.System")


@Property(name="spec.name", value="BodyRoutesTest")
@Property(name="uploads.directory", value="${java.io.tmpdir}")
@MicronautTest
class BodyRoutesTest:
    client: Annotated[HttpClient, Inject, Client("/")]
    items: Annotated[ItemRepository, Inject]

    @Test
    def a_decoded_body(self):
        response = self.client.toBlocking().exchange(HttpRequest.POST("/async/items", Map.of("id", 0, "name", "lamp")), Map)
        assert response.getStatus() == HttpStatus.CREATED
        assert response.body().get("name") == "lamp"

    @Test
    def the_elements_of_a_json_array(self):
        response = self.client.toBlocking().exchange(HttpRequest.POST("/async/items/import", List.of(
            Map.of("id", 101, "name", "a"), Map.of("id", 102, "name", "b"), Map.of("id", 103, "name", "c"))))
        assert response.getStatus() == HttpStatus.ACCEPTED
        assert self.items.find(102).name == "b"

    @Test
    def bounded_text(self):
        http = self.client.toBlocking()
        assert http.retrieve(HttpRequest.POST("/async/notes", "hello").contentType(MediaType.TEXT_PLAIN_TYPE)) == "received 5 characters"
        try:
            http.retrieve(HttpRequest.POST("/async/notes", "x" * 2048).contentType(MediaType.TEXT_PLAIN_TYPE))
            assert False
        except HttpClientResponseException as e:
            assert e.getStatus() == HttpStatus.REQUEST_ENTITY_TOO_LARGE

    @Test
    def a_body_written_to_a_file(self):
        content = bytes(i % 256 for i in range(100_000))
        name = self.client.toBlocking().retrieve(HttpRequest.PUT("/async/files", content).contentType(MediaType.APPLICATION_OCTET_STREAM_TYPE))
        file = Path.of(System.getProperty("java.io.tmpdir"), name)
        try:
            with open(str(file), "rb") as written:
                assert written.read() == content
        finally:
            Files.deleteIfExists(file)

    @Test
    def a_request_rejected_without_reading_the_body(self):
        http = self.client.toBlocking()
        try:
            http.retrieve(HttpRequest.POST("/async/guarded", bytes(10)).contentType(MediaType.APPLICATION_OCTET_STREAM_TYPE))
            assert False
        except HttpClientResponseException as e:
            assert e.getStatus() == HttpStatus.UNAUTHORIZED
        assert http.retrieve(HttpRequest.POST("/async/guarded", bytes(10))
                             .contentType(MediaType.APPLICATION_OCTET_STREAM_TYPE)
                             .header("X-Token", "secret")) == "accepted 10 bytes"
