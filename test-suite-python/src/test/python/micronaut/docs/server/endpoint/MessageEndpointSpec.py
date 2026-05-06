from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http import HttpRequest, MediaType
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Disabled, Test

String = java.type("java.lang.String")


@Property(name="endpoints.message.enabled", value=True)
@Property(name="spec.name", value="MessageEndpointSpec")
@MicronautTest
class MessageEndpointSpec:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    @Disabled("Python management endpoint write/delete routing is not validated yet")
    def testWriteMessageEndpoint(self) -> None:
        response = self.client.toBlocking().exchange(
            HttpRequest.POST("/message", {"newMessage": "A new message"})
                .contentType(MediaType.APPLICATION_FORM_URLENCODED),
            String,
        )
        assert response.body() == "Message updated"
