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

Map = java.type("java.util.Map")
String = java.type("java.lang.String")

BUFFER_LIMIT = 256 * 1024


@Property(name="spec.name", value="PeopleControllerSpec")
@Property(name="micronaut.server.max-request-buffer-size", value="262144")
@MicronautTest
class PeopleControllerSpec:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def decodes_the_body(self):
        response = self.client.toBlocking().exchange(
            HttpRequest.POST("/people", '{"name":"Fred","age":45}').contentType(MediaType.APPLICATION_JSON_TYPE),
            Map,
        )
        assert response.getStatus() == HttpStatus.CREATED
        person = response.getBody().get()
        assert person.get("name") == "Fred"
        assert person.get("age") == 45

    @Test
    def a_malformed_body_is_a_bad_request(self):
        assert self.status("/people", '{"name":', MediaType.APPLICATION_JSON_TYPE) == HttpStatus.BAD_REQUEST

    @Test
    def a_body_larger_than_the_buffer_limit_is_too_large(self):
        json = '{"name":"' + "x" * (4 * BUFFER_LIMIT) + '","age":45}'
        assert self.status("/people", json, MediaType.APPLICATION_JSON_TYPE) == HttpStatus.REQUEST_ENTITY_TOO_LARGE

    @Test
    def imports_the_elements_of_a_json_array(self):
        array = "[" + ",".join('{"name":"' + "x" * 1000 + str(i) + '","age":' + str(i) + "}" for i in range(2000)) + "]"
        # the limit applies to each element, not to the whole body
        assert len(array) > 4 * BUFFER_LIMIT
        assert self.post("/people/import", array, MediaType.APPLICATION_JSON_TYPE) == "Imported 2000"

    @Test
    def imports_the_elements_of_a_json_stream(self):
        stream = '{"name":"Fred","age":45}\n{"name":"Wilma","age":40}\n'
        assert self.post("/people/import", stream, MediaType.APPLICATION_JSON_STREAM_TYPE) == "Imported 2"

    @Test
    def reads_the_body_as_text(self):
        assert self.post("/people/notes", "Call Fred", MediaType.TEXT_PLAIN_TYPE) == "Received 9 characters"

    @Test
    def a_filter_reads_a_copy_and_the_controller_reads_the_body(self):
        assert self.post("/messages", "Hello Fred", MediaType.TEXT_PLAIN_TYPE) == "Received Hello Fred"
        assert self.status("/messages", "Buy spam", MediaType.TEXT_PLAIN_TYPE) == HttpStatus.BAD_REQUEST

    def post(self, uri: str, body: str, content_type) -> str:
        return self.client.toBlocking().retrieve(HttpRequest.POST(uri, body).contentType(content_type), String)

    def status(self, uri: str, body: str, content_type):
        try:
            self.post(uri, body, content_type)
        except HttpClientResponseException as e:
            return e.getStatus()
        raise AssertionError("expected an error response from " + uri)
