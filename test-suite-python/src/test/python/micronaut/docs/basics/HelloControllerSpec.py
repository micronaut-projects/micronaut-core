from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.core.type import Argument
from micronaut.http import HttpRequest, HttpStatus, MediaType
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.http.uri import UriBuilder
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Disabled, Test

Flux = java.type("reactor.core.publisher.Flux")
Map = java.type("java.util.Map")
String = java.type("java.lang.String")
MessageClass = java.type("micronaut.docs.basics.Message")

from .Message import Message


@Property(name="spec.name", value="HelloControllerSpec")
@MicronautTest
class HelloControllerSpec:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def testSimpleRetrieve(self):
        # tag::simple[]
        uri = str(UriBuilder.of("/hello/{name}").expand({"name": "John"}))
        assert "/hello/John" == uri

        result = self.client.toBlocking().retrieve(uri)

        assert "Hello John" == result
        # end::simple[]

    @Test
    def testRetrieveWithHeaders(self):
        # tag::headers[]
        response = getattr(Flux, "from")(
            self.client.retrieve(
                HttpRequest.GET("/hello/John").header("X-My-Header", "SomeValue")
            )
        )
        # end::headers[]

        assert "Hello John" == response.blockFirst()

    @Test
    def testRetrieveWithJSON(self):
        # tag::jsonmap[]
        response = getattr(Flux, "from")(
            self.client.retrieve(HttpRequest.GET("/greet/John"), Map)
        )
        # end::jsonmap[]

        assert "Hello John" == response.blockFirst().get("text")

        # tag::jsonmaptypes[]
        response = getattr(Flux, "from")(
            self.client.retrieve(
                HttpRequest.GET("/greet/John"),
                Argument.of(Map, String, String),  # <1>
            )
        )
        # end::jsonmaptypes[]

        assert "Hello John" == response.blockFirst().get("text")

    @Test
    @Disabled("Python HTTP client returns generated Java wrapper objects for Python dataclass responses")
    def testRetrieveWithPOJO(self):
        # tag::jsonpojo[]
        response = getattr(Flux, "from")(
            self.client.retrieve(HttpRequest.GET("/greet/John"), MessageClass)
        )

        assert "Hello John" == response.blockFirst().text
        # end::jsonpojo[]

    @Test
    @Disabled("Python HTTP client returns generated Java wrapper objects for Python dataclass responses")
    def testRetrieveWithPOJOResponse(self):
        # tag::pojoresponse[]
        call = getattr(Flux, "from")(
            self.client.exchange(
                HttpRequest.GET("/greet/John"), MessageClass  # <1>
            )
        )

        response = call.blockFirst()
        message = response.getBody(MessageClass)  # <2>
        assert HttpStatus.OK == response.getStatus()  # <3>
        assert message.isPresent()
        assert "Hello John" == message.get().text
        # end::pojoresponse[]

    @Test
    @Disabled("Python @Status on controller methods is ignored, returning OK with the expected body")
    def testPostRequestWithString(self):
        # tag::poststring[]
        call = getattr(Flux, "from")(
            self.client.exchange(
                HttpRequest.POST("/hello", "Hello John")  # <1>
                .contentType(MediaType.TEXT_PLAIN_TYPE)
                .accept(MediaType.TEXT_PLAIN_TYPE),  # <2>
                String,  # <3>
            )
        )
        # end::poststring[]

        response = call.blockFirst()
        message = response.getBody(String)  # <2>
        assert HttpStatus.CREATED == response.getStatus(), f"status={response.getStatus()}, body={message}"  # <3>
        assert message.isPresent()
        assert "Hello John" == message.get()

    @Test
    @Disabled("Python @Status on controller methods is ignored, returning OK with the expected body")
    def testPostRequestWithPOJO(self):
        # tag::postpojo[]
        call = getattr(Flux, "from")(
            self.client.exchange(
                HttpRequest.POST("/greet", Message("Hello John")),  # <1>
                MessageClass,  # <2>
            )
        )
        # end::postpojo[]

        response = call.blockFirst()
        message = response.getBody(MessageClass)  # <2>
        assert HttpStatus.CREATED == response.getStatus(), f"status={response.getStatus()}, body={message}"  # <3>
        assert message.isPresent()
        assert "Hello John" == message.get().text
