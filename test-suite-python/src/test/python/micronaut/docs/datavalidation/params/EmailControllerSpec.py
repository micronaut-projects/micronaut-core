from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.http.client.exceptions import HttpClientResponseException
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Disabled, Test

HttpStatus = java.type("io.micronaut.http.HttpStatus")


@Property(name="spec.name", value="datavalidationparams")
@MicronautTest
class EmailControllerSpec:
    client: Annotated[HttpClient, Inject, Client("/")]

    # tag::paramsvalidated[]
    @Test
    @Disabled("Python HTTP validation metadata is generated, but invalid requests are not rejected yet")
    def test_parameters_are_validated(self):
        try:
            self.client.toBlocking().exchange("/email/send?subject=Hi&recipient=")
            assert False
        except HttpClientResponseException as e:
            response = e.getResponse()

        assert response.getStatus() == HttpStatus.BAD_REQUEST

        response = self.client.toBlocking().exchange("/email/send?subject=Hi&recipient=me@micronaut.example")

        assert response.getStatus() == HttpStatus.OK
    # end::paramsvalidated[]
