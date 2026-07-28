from dataclasses import dataclass
from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.core.annotation import Introspected, ReflectiveAccess
from micronaut.http import MediaType
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.http.client.exceptions import HttpClientResponseException
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

CodecException = java.type("io.micronaut.http.codec.CodecException")
HttpRequest = java.type("io.micronaut.http.HttpRequest")


@ReflectiveAccess
@Introspected
@dataclass
class Book:
    title: str | None = None
    pages: int | None = None


@Property(name="spec.name", value="consumesspec")
@MicronautTest
class ConsumesControllerSpec:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def test_consumes(self):
        book = Book("The Stand", 1000)

        try:
            self.client.toBlocking().exchange(
                HttpRequest.POST("/consumes", book).contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
            )
            assert False
        except HttpClientResponseException:
            pass

        self.client.toBlocking().exchange(
            HttpRequest.POST("/consumes", book).contentType(MediaType.APPLICATION_JSON)
        )

        self.client.toBlocking().exchange(
            HttpRequest.POST("/consumes/multiple", book).contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
        )

        self.client.toBlocking().exchange(
            HttpRequest.POST("/consumes/multiple", book).contentType(MediaType.APPLICATION_JSON)
        )

        try:
            self.client.toBlocking().exchange(
                HttpRequest.POST("/consumes/member", book).contentType(MediaType.TEXT_PLAIN)
            )
            assert False
        except CodecException:
            pass
