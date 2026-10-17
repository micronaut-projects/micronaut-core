from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http import HttpRequest
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

String = java.type("java.lang.String")
ZonedDateTime = java.type("java.time.ZonedDateTime")
DateTimeFormatter = java.type("java.time.format.DateTimeFormatter")


@Property(name="spec.name", value="BenchmarkRoutesTest")
@MicronautTest
class BenchmarkRoutesTest:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def the_plaintext_and_json_tests(self):
        http = self.client.toBlocking()
        plaintext = http.exchange(HttpRequest.GET("/plaintext"), String)
        assert plaintext.body() == "Hello, World!"
        assert plaintext.getHeaders().get("Content-Type") == "text/plain"
        assert plaintext.getHeaders().get("Content-Length") == "13"
        assert plaintext.getHeaders().get("Server") == "Micronaut"
        ZonedDateTime.parse(plaintext.getHeaders().get("Date"), DateTimeFormatter.RFC_1123_DATE_TIME)
        assert plaintext.getHeaders().get("Content-Encoding") is None

        json = http.exchange(HttpRequest.GET("/json"), String)
        assert json.body() == '{"message":"Hello, World!"}'
        assert json.getHeaders().get("Content-Type") == "application/json"
        assert json.getHeaders().get("Content-Length") == "27"
        assert json.getHeaders().get("Server") == "Micronaut"
        ZonedDateTime.parse(json.getHeaders().get("Date"), DateTimeFormatter.RFC_1123_DATE_TIME)
