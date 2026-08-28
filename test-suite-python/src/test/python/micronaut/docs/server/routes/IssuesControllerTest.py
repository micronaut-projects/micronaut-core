from typing import Annotated

# tag::imports[]
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test
# end::imports[]

import java

HttpClientResponseException = java.type("io.micronaut.http.client.exceptions.HttpClientResponseException")


# tag::startclass[]
@Property(name="spec.name", value="IssuesControllerTest")
@MicronautTest
class IssuesControllerTest:
    client: Annotated[HttpClient, Inject, Client("/")]
    # end::startclass[]

    # tag::normal[]
    @Test
    def test_issue(self):
        body = self.client.toBlocking().retrieve("/issues/12")  # <3>

        assert body is not None
        assert body == "Issue # 12!"  # <4>

    @Test
    def test_issue_from_id(self):
        body = self.client.toBlocking().retrieve("/issues/issue/13")

        assert body is not None
        assert body == "Issue # 13!"  # <5>

    @Test
    def test_show_with_invalid_integer(self):
        try:
            self.client.toBlocking().exchange("/issues/hello")
            assert False, "Expected invalid integer to fail"
        except HttpClientResponseException as e:
            assert e.getStatus().getCode() == 400  # <6>

    @Test
    def test_issue_without_number(self):
        try:
            self.client.toBlocking().exchange("/issues/")
            assert False, "Expected missing route to fail"
        except HttpClientResponseException as e:
            assert e.getStatus().getCode() == 404  # <7>
    # end::normal[]

    # tag::defaultvalue[]
    @Test
    def test_default_issue(self):
        body = self.client.toBlocking().retrieve("/issues/default")

        assert body is not None
        assert body == "Issue # 0!"  # <1>

    @Test
    def test_not_default_issue(self):
        body = self.client.toBlocking().retrieve("/issues/default/1")

        assert body is not None
        assert body == "Issue # 1!"  # <2>
    # end::defaultvalue[]

# tag::endclass[]
# end::endclass[]
