from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http import HttpHeaders, HttpRequest, HttpStatus
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

String = java.type("java.lang.String")


@Property(name="spec.name", value="PreparedRoutesTest")
@MicronautTest
class PreparedRoutesTest:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def a_function_answers_with_a_prepared_response(self):
        http = self.client.toBlocking()
        current = http.exchange(HttpRequest.GET("/release"), String)
        assert current.getStatus() == HttpStatus.OK
        assert current.body() == "release 2026.10"
        assert current.getHeaders().get(HttpHeaders.ETAG) == '"2026.10"'

        not_modified = http.exchange(HttpRequest.GET("/release").header(HttpHeaders.IF_NONE_MATCH, '"2026.10"'), String)
        assert not_modified.getStatus() == HttpStatus.NOT_MODIFIED
        assert not_modified.getHeaders().get(HttpHeaders.ETAG) == '"2026.10"'
