from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

HashMap = java.type("java.util.HashMap")
HttpStatus = java.type("io.micronaut.http.HttpStatus")
UriTemplate = java.type("io.micronaut.http.uri.UriTemplate")


@MicronautTest
class BookmarkControllerSpec:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def test_binding_pagination(self):
        template = UriTemplate("/api/bookmarks/list{?offset,max,sort,order}")
        params = HashMap()
        params.put("offset", 0)
        params.put("max", 10)

        response = self.client.toBlocking().exchange(template.expand(params))

        assert response.status() == HttpStatus.OK
