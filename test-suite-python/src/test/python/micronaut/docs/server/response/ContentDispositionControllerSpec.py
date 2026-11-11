from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

HttpHeaders = java.type("io.micronaut.http.HttpHeaders")
HttpRequest = java.type("io.micronaut.http.HttpRequest")
String = java.type("java.lang.String")


@Property(name="spec.name", value="contentdisposition")
@MicronautTest
class ContentDispositionControllerSpec:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def test_attachment_with_filename(self):
        response = self.client.toBlocking().exchange(HttpRequest.GET("/content-disposition/report"), String)

        assert response.header(HttpHeaders.CONTENT_DISPOSITION) == 'attachment; filename="report.csv"; filename*=utf-8\'\'report.csv'

    @Test
    def test_inline(self):
        response = self.client.toBlocking().exchange(HttpRequest.GET("/content-disposition/preview"), String)

        assert response.header(HttpHeaders.CONTENT_DISPOSITION) == "inline"
