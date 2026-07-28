from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.core.type import Argument
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.http.client.exceptions import HttpClientResponseException
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test
from org.junit.jupiter.api import Assertions

Flux = java.type("reactor.core.publisher.Flux")
HttpRequest = java.type("io.micronaut.http.HttpRequest")
HttpStatus = java.type("io.micronaut.http.HttpStatus")
Map = java.type("java.util.Map")
Person = java.type("micronaut.docs.server.json.Person")


@Property(name="spec.name", value="PersonControllerSpec")
@MicronautTest
class PersonControllerSpec:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def test_global_error_handler(self):
        try:
            Flux.from_(self.client.exchange("/people/error", Map)).blockFirst()
            assert False
        except HttpClientResponseException as e:
            response = e.getResponse()

            assert response.getStatus() == HttpStatus.INTERNAL_SERVER_ERROR
            assert response.getBody().get().get("message") == "Bad Things Happened: Something went wrong"

    @Test
    def test_save(self):
        response = self.client.toBlocking().exchange(
            HttpRequest.POST("/people", "{\"firstName\":\"Fred\",\"lastName\":\"Flintstone\",\"age\":45}"),
            Person,
        )
        assert response.getBody().isPresent()
        person = response.getBody().get()

        assert person.getFirstName() == "Fred"
        assert response.getStatus() == HttpStatus.CREATED

        response = self.client.toBlocking().exchange(HttpRequest.GET("/people/Fred"), Person)
        person = response.getBody().get()

        assert person.getFirstName() == "Fred"
        assert response.getStatus() == HttpStatus.OK

    @Test
    def test_save_reactive(self):
        response = self.client.toBlocking().exchange(
            HttpRequest.POST("/people/saveReactive", "{\"firstName\":\"Wilma\",\"lastName\":\"Flintstone\",\"age\":36}"),
            Person,
        )
        assert response.getBody().isPresent()
        person = response.getBody().get()

        assert person.getFirstName() == "Wilma"
        assert response.getStatus() == HttpStatus.CREATED

    @Test
    def test_save_future(self):
        response = self.client.toBlocking().exchange(
            HttpRequest.POST("/people/saveFuture", "{\"firstName\":\"Pebbles\",\"lastName\":\"Flintstone\",\"age\":0}"),
            Person,
        )
        assert response.getBody().isPresent()
        person = response.getBody().get()

        assert person.getFirstName() == "Pebbles"
        assert response.getStatus() == HttpStatus.CREATED

    @Test
    def test_save_args(self):
        response = self.client.toBlocking().exchange(
            HttpRequest.POST("/people/saveWithArgs", "{\"firstName\":\"Dino\",\"lastName\":\"Flintstone\",\"age\":3}"),
            Person,
        )
        assert response.getBody().isPresent()
        person = response.getBody().get()

        assert person.getFirstName() == "Dino"
        assert response.getStatus() == HttpStatus.CREATED

    @Test
    def test_person_not_found(self):
        e = Assertions.assertThrows(
            HttpClientResponseException,
            lambda: Flux.from_(self.client.exchange("/people/Sally", Map)).blockFirst(),
        )
        response = e.getResponse()
        Assertions.assertEquals("Person Not Found", response.getBody().get().get("message"), str(response.getBody()))
        Assertions.assertEquals(HttpStatus.NOT_FOUND, response.getStatus())

    @Test
    def test_save_invalid_json(self):
        try:
            self.client.toBlocking().exchange(
                HttpRequest.POST("/people", "{\""),
                Argument.of(Person),
                Argument.of(Map),
            )
            assert False
        except HttpClientResponseException as e:
            response = e.getResponse()
            assert str(response.getBody(Map).get().get("message")).startswith("Invalid JSON: Unexpected end-of-input")
            assert response.getStatus() == HttpStatus.BAD_REQUEST
