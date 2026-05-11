from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.http.client.exceptions import HttpClientResponseException
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Disabled, Test

HttpRequest = java.type("io.micronaut.http.HttpRequest")
HttpStatus = java.type("io.micronaut.http.HttpStatus")
Email = java.type("micronaut.docs.datavalidation.pogo.Email")


@Property(name="spec.name", value="datavalidationpogo")
@MicronautTest
class EmailControllerSpec:
    client: Annotated[HttpClient, Inject, Client("/")]

    # tag::pojovalidated[]
    @Test
    @Disabled("GraalPy Java exception matching currently fails for the propagated body validation exception")
    def test_pojo_validation(self):
        try:
            email = Email()
            email.setSubject("Hi")
            email.setRecipient("")
            self.client.toBlocking().exchange(HttpRequest.POST("/email/send", email))
            assert False
        except HttpClientResponseException as e:
            response = e.getResponse()

        assert response.getStatus() == HttpStatus.BAD_REQUEST

        email = Email()
        email.setSubject("Hi")
        email.setRecipient("me@micronaut.example")
        response = self.client.toBlocking().exchange(HttpRequest.POST("/email/send", email))

        assert response.getStatus() == HttpStatus.OK
    # end::pojovalidated[]
