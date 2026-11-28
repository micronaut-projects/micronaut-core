from typing import Annotated

from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http import HttpRequest
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test


@Property(name="spec.name", value="ReportRoutesTest")
@MicronautTest
class ReportRoutesTest:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def the_group_timeout_applies(self):
        assert self.client.toBlocking().retrieve(HttpRequest.GET("/fn-reports/summary")) == "summary"
