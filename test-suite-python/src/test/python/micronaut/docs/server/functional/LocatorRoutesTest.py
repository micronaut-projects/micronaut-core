from typing import Annotated

from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http import HttpRequest, HttpStatus
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.http.client.exceptions import HttpClientResponseException
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test


@Property(name="spec.name", value="LocatorRoutesTest")
@MicronautTest
class LocatorRoutesTest:
    client: Annotated[HttpClient, Inject, Client("/")]

    def _status(self, request) -> HttpStatus:
        try:
            self.client.toBlocking().retrieve(request)
        except HttpClientResponseException as e:
            return e.getStatus()
        raise AssertionError("the request did not fail")

    @Test
    def the_routes_of_the_located_target_answer(self):
        http = self.client.toBlocking()
        for prefix in ["/shops", "/remote-shops"]:
            assert http.retrieve(HttpRequest.GET(prefix + "/north")) == "north"
            assert http.retrieve(HttpRequest.GET(prefix + "/north/items/1")) == "coffee"
            assert http.retrieve(HttpRequest.GET(prefix + "/south/items/0")) == "juice"
            assert self._status(HttpRequest.GET(prefix + "/west/items/0")) == HttpStatus.NOT_FOUND
            assert self._status(HttpRequest.DELETE(prefix + "/north/items/0")) == HttpStatus.METHOD_NOT_ALLOWED
        # the routes the function chose for the located shop
        assert http.retrieve(HttpRequest.GET("/archived-shops/north/items/1")) == "coffee"
        assert http.retrieve(HttpRequest.GET("/archived-shops/south/item")) == "juice"
        assert self._status(HttpRequest.GET("/archived-shops/south/items/0")) == HttpStatus.NOT_FOUND
