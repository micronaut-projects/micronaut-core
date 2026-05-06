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
Email = java.type("micronaut.docs.datavalidation.groups.Email")


@Property(name="spec.name", value="datavalidationgroups")
@MicronautTest
class EmailControllerSpec:
    client: Annotated[HttpClient, Inject, Client("/")]

    # tag::pojovalidateddefault[]
    @Test
    @Disabled("Python @Valid body validation metadata is generated, but invalid requests are not rejected yet")
    def test_pojo_validation_default_group(self):
        try:
            email = Email()
            email.setSubject("")
            email.setRecipient("")
            self.client.toBlocking().exchange(HttpRequest.POST("/email/createDraft", email))
            assert False
        except HttpClientResponseException as e:
            response = e.getResponse()

        assert response.getStatus() == HttpStatus.BAD_REQUEST

        email = Email()
        email.setSubject("Hi")
        email.setRecipient("")
        response = self.client.toBlocking().exchange(HttpRequest.POST("/email/createDraft", email))

        assert response.getStatus() == HttpStatus.OK
    # end::pojovalidateddefault[]

    # tag::pojovalidatedfinal[]
    @Test
    @Disabled("Python validation groups metadata is generated, but invalid requests are not rejected yet")
    def test_pojo_validation_final_validation_group(self):
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
    # end::pojovalidatedfinal[]
