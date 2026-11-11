from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http import HttpRequest, HttpStatus
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.http.client.exceptions import HttpClientResponseException
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

String = java.type("java.lang.String")


@Property(name="spec.name", value="AuditedRoutesTest")
@Property(name="micronaut.router.versioning.enabled", value="true")
@Property(name="micronaut.router.versioning.header.enabled", value="true")
@MicronautTest
class AuditedRoutesTest:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def the_annotations_of_a_route_bind_a_filter(self):
        http = self.client.toBlocking()
        payment = http.exchange(HttpRequest.POST("/payments/10", ""), String)
        assert payment.body() == "paid 10"
        assert payment.getHeaders().get("X-Audited") == "true"
        refund = http.exchange(HttpRequest.POST("/refunds/5", ""), String)
        assert refund.body() == "refunded 5"
        assert refund.getHeaders().get("X-Audited") == "true"
        prices = http.exchange(HttpRequest.GET("/prices"), String)
        assert prices.body() == "prices"
        assert prices.getHeaders().get("X-Audited") is None

    @Test
    def the_version_annotation_of_a_route_selects_it(self):
        http = self.client.toBlocking()
        v1 = http.exchange(HttpRequest.GET("/receipts/7").header("X-API-VERSION", "1"), String)
        assert v1.body() == "receipt v1 7"
        assert v1.getHeaders().get("X-Audited") is None
        v2 = http.exchange(HttpRequest.GET("/receipts/7").header("X-API-VERSION", "2"), String)
        assert v2.body() == "receipt v2 7"
        assert v2.getHeaders().get("X-Audited") == "true"

    @Test
    def the_routes_of_a_group_have_its_executor_and_annotations(self):
        http = self.client.toBlocking()
        users = http.exchange(HttpRequest.GET("/admin/users").header("X-API-VERSION", "2"), String)
        # the blocking executor, not the event loop
        assert "EventLoop" not in users.body()
        assert users.getHeaders().get("X-Audited") == "true"
        deleted = http.exchange(HttpRequest.DELETE("/admin/users/3").header("X-API-VERSION", "2"), String)
        assert deleted.body() == "deleted 3"
        assert deleted.getHeaders().get("X-Audited") == "true"
        # the routes of the group answer the version 2 only
        try:
            http.exchange(HttpRequest.GET("/admin/users").header("X-API-VERSION", "1"), String)
            assert False
        except HttpClientResponseException as e:
            assert e.getStatus() == HttpStatus.NOT_FOUND

    @Test
    def a_declared_route(self):
        assert self.client.toBlocking().retrieve(HttpRequest.GET("/balance/main")) == "balance of main"
