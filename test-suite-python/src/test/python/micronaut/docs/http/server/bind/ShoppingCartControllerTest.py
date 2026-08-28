from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.core.type import Argument
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.http.client.exceptions import HttpClientResponseException
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

Cookie = java.type("io.micronaut.http.cookie.Cookie")
HttpRequest = java.type("io.micronaut.http.HttpRequest")
Map = java.type("java.util.Map")
Object = java.type("java.lang.Object")
String = java.type("java.lang.String")


@MicronautTest
class ShoppingCartControllerTest:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def test_binding_bad_credentials(self):
        request = HttpRequest.GET("/customBinding/annotated").cookie(
            Cookie.of("shoppingCart", "{}")
        )

        try:
            self.client.toBlocking().exchange(request)
            assert False
        except HttpClientResponseException as responseException:
            body = responseException.getResponse().getBody(Map).get()
            embedded = body.get("_embedded")
            message = embedded.get("errors")[0].get("message")

            assert message == "Required ShoppingCart [sessionId] not specified"

    @Test
    def test_annotation_binding(self):
        request = HttpRequest.GET("/customBinding/annotated").cookie(
            Cookie.of("shoppingCart", "{\"sessionId\": 5}")
        )
        response = self.client.toBlocking().retrieve(request)

        assert response == "Session:5"

    @Test
    def test_type_binding(self):
        request = HttpRequest.GET("/customBinding/typed").cookie(
            Cookie.of("shoppingCart", "{\"sessionId\": 5, \"total\": 20}")
        )

        body = self.client.toBlocking().retrieve(
            request,
            Argument.mapOf(String, Object),
        )

        assert body.get("sessionId") == "5"
        assert body.get("total") == 20
