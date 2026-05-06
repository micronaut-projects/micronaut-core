from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http import MediaType
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

HttpRequest = java.type("io.micronaut.http.HttpRequest")
String = java.type("java.lang.String")


@Property(name="spec.name", value="producesspec")
@MicronautTest
class ProducesControllerSpec:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def test_content_types(self):
        response = self.client.toBlocking().exchange(HttpRequest.GET("/produces"), String)

        assert response.getContentType().get() == MediaType.APPLICATION_JSON_TYPE

        response = self.client.toBlocking().exchange(HttpRequest.GET("/produces/html"), String)

        assert response.getContentType().get() == MediaType.TEXT_HTML_TYPE

        response = self.client.toBlocking().exchange(HttpRequest.GET("/produces/xml"), String)

        assert response.getContentType().get() == MediaType.TEXT_XML_TYPE
