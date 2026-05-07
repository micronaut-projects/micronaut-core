from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.http import HttpRequest, HttpStatus, MediaType
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .Book import Book

Flux = java.type("reactor.core.publisher.Flux")
BookClass = java.type("micronaut.docs.basics.Book")


@MicronautTest
class BookControllerSpec:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def testPostWithURITemplate(self):
        # tag::posturitemplate[]
        call = getattr(Flux, "from")(
            self.client.exchange(
                HttpRequest.POST("/amazon/book/{title}", Book("The Stand")),
                BookClass,
            )
        )
        # end::posturitemplate[]

        response = call.blockFirst()
        message = response.getBody(BookClass)  # <2>
        assert HttpStatus.CREATED == response.getStatus(), f"status={response.getStatus()}, body={message}"  # <3>
        assert message.isPresent()
        assert "The Stand" == message.get().title

    @Test
    def testPostFormData(self):
        # tag::postform[]
        call = getattr(Flux, "from")(
            self.client.exchange(
                HttpRequest.POST("/amazon/book/{title}", Book("The Stand"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED),
                BookClass,
            )
        )
        # end::postform[]

        response = call.blockFirst()
        message = response.getBody(BookClass)  # <2>
        assert HttpStatus.CREATED == response.getStatus(), f"status={response.getStatus()}, body={message}"  # <3>
        assert message.isPresent()
        assert "The Stand" == message.get().title
