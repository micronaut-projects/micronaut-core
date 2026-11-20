from typing import Annotated

import hashlib
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http import HttpRequest, HttpStatus, MediaType
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.http.client.exceptions import HttpClientResponseException
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test


@Property(name="spec.name", value="ChecksumFilterSpec")
@MicronautTest
class ChecksumFilterSpec:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def the_filter_reads_the_body_and_the_controller_reads_it_again(self):
        body = "x" * (256 * 1024)
        digest = hashlib.sha256(body.encode("utf-8")).hexdigest()
        response = self.client.toBlocking().retrieve(
            HttpRequest.POST("/checksum", body)
            .contentType(MediaType.TEXT_PLAIN_TYPE)
            .header("X-Content-SHA256", digest)
        )
        assert response == "Received " + str(len(body)) + " characters"

    @Test
    def a_mismatch_is_rejected(self):
        try:
            self.client.toBlocking().retrieve(
                HttpRequest.POST("/checksum", "hello")
                .contentType(MediaType.TEXT_PLAIN_TYPE)
                .header("X-Content-SHA256", "wrong")
            )
            assert False, "expected a bad request"
        except HttpClientResponseException as e:
            assert e.getStatus() == HttpStatus.BAD_REQUEST
