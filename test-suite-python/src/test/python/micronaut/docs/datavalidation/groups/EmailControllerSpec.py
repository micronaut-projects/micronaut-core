from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.http.client.exceptions import HttpClientResponseException
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

HttpRequest = java.type("io.micronaut.http.HttpRequest")
HttpStatus = java.type("io.micronaut.http.HttpStatus")
Email = java.type("micronaut.docs.datavalidation.groups.Email")


@Property(name="spec.name", value="datavalidationgroups")
@MicronautTest
class EmailControllerSpec:
    client: Annotated[HttpClient, Inject, Client("/")]

    # tag::pojovalidateddefault[]
    @Test
    def test_pojo_validation_default_group(self):
        try:
            email = Email("", "")
            self.client.toBlocking().exchange(HttpRequest.POST("/email/createDraft", email))
        except BaseException as e:
            response = e.getResponse()
        else:
            assert False

        assert response.getStatus() == HttpStatus.BAD_REQUEST

        email = Email("Hi", "")
        response = self.client.toBlocking().exchange(HttpRequest.POST("/email/createDraft", email))

        assert response.getStatus() == HttpStatus.OK
    # end::pojovalidateddefault[]

    # tag::pojovalidatedfinal[]
    @Test
    def test_pojo_validation_final_validation_group(self):
        try:
            email = Email("Hi", "")
            self.client.toBlocking().exchange(HttpRequest.POST("/email/send", email))
        except BaseException as e:
            response = e.getResponse()
        else:
            assert False

        assert response.getStatus() == HttpStatus.BAD_REQUEST

        email = Email("Hi", "me@micronaut.example")
        response = self.client.toBlocking().exchange(HttpRequest.POST("/email/send", email))

        assert response.getStatus() == HttpStatus.OK
    # end::pojovalidatedfinal[]
