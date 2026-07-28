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


@Property(name="spec.name", value="VersionedControllerSpec")
@Property(name="micronaut.router.versioning.enabled", value="true")
@Property(name="micronaut.router.versioning.header.enabled", value="true")
@MicronautTest
class VersionedControllerSpec:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def test_header_versioning(self):
        blocking_client = self.client.toBlocking()

        assert blocking_client.retrieve(
            HttpRequest.GET("/versioned/hello").header("X-API-VERSION", "1"),
            String,
        ) == "helloV1"

        assert blocking_client.retrieve(
            HttpRequest.GET("/versioned/hello").header("X-API-VERSION", "2"),
            String,
        ) == "helloV2"
