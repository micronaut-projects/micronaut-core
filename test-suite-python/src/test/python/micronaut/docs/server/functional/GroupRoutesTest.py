from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http import HttpRequest, HttpStatus, MediaType
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.http.client.exceptions import HttpClientResponseException
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

Map = java.type("java.util.Map")
String = java.type("java.lang.String")


@Property(name="spec.name", value="GroupRoutesTest")
@MicronautTest
class GroupRoutesTest:
    client: Annotated[HttpClient, Inject, Client("/")]

    def _fails(self, request) -> HttpClientResponseException:
        try:
            self.client.toBlocking().exchange(request, String)
        except HttpClientResponseException as e:
            return e
        raise AssertionError("the request did not fail")

    @Test
    def the_group_filters_apply_to_every_route_of_the_group(self):
        orders = self.client.toBlocking().exchange(HttpRequest.GET("/api/orders").header("X-Tenant", "acme"), String)
        assert orders.body() == "orders of acme"
        assert orders.getHeaders().get("X-Api") == "v1"
        assert orders.getHeaders().get("X-Served-By") == "api"
        no_tenant = self._fails(HttpRequest.GET("/api/orders"))
        assert no_tenant.getStatus() == HttpStatus.BAD_REQUEST
        assert no_tenant.getResponse().getHeaders().get("X-Api") == "v1"

    @Test
    def a_route_without_a_path_is_at_the_prefix_of_the_group(self):
        assert self.client.toBlocking().retrieve(HttpRequest.GET("/api").header("X-Tenant", "acme")) == "api of acme"

    @Test
    def a_nested_group_adds_its_filters(self):
        forbidden = self._fails(HttpRequest.GET("/api/admin/users").header("X-Tenant", "acme"))
        assert forbidden.getStatus() == HttpStatus.FORBIDDEN
        assert self.client.toBlocking().retrieve(
            HttpRequest.GET("/api/admin/users").header("X-Tenant", "acme").header("X-Role", "admin")) == "users"

    @Test
    def route_filters_change_the_request_and_replace_the_response(self):
        assert self.client.toBlocking().retrieve(
            HttpRequest.GET("/api/reports/7").header("X-Tenant", "acme")) == "report 7 as summary"
        gone = self._fails(HttpRequest.GET("/api/reports/7").header("X-Tenant", "acme").header("X-Legacy", "true"))
        assert gone.getStatus() == HttpStatus.GONE

    @Test
    def server_filters_filter_every_request_of_their_patterns(self):
        not_found = self._fails(HttpRequest.GET("/api/missing").header("X-Tenant", "acme"))
        assert not_found.getStatus() == HttpStatus.NOT_FOUND
        assert not_found.getResponse().getHeaders().get("X-Served-By") == "api"
        assert not_found.getResponse().getHeaders().get("X-Api") is None
        assert self.client.toBlocking().retrieve(HttpRequest.GET("/v1/orders").header("X-Tenant", "acme")) == "orders of acme"

    @Test
    def the_routes_of_a_group_have_its_media_types_and_its_executor(self):
        http = self.client.toBlocking()
        saved = http.exchange(HttpRequest.POST("/notes", "hello").contentType(MediaType.TEXT_PLAIN_TYPE), String)
        assert saved.body() == "saved hello"
        assert saved.getContentType().get().getName() == MediaType.TEXT_PLAIN
        unsupported = self._fails(HttpRequest.POST("/notes", "{}").contentType(MediaType.APPLICATION_JSON_TYPE))
        assert unsupported.getStatus() == HttpStatus.UNSUPPORTED_MEDIA_TYPE
        assert http.retrieve(HttpRequest.POST("/notes/items", Map.of("id", 1, "name", "pen"))
                             .contentType(MediaType.APPLICATION_JSON_TYPE)) == "saved pen"
        assert http.retrieve(HttpRequest.GET("/notes/count")) == "1"
        drafts = http.exchange(HttpRequest.GET("/notes/drafts"), String)
        assert drafts.getContentType().get().getName() == MediaType.APPLICATION_JSON
        assert drafts.body() == '[{"id":1,"name":"draft"}]'
        not_acceptable = self._fails(HttpRequest.GET("/notes/drafts").accept(MediaType.TEXT_PLAIN_TYPE))
        assert not_acceptable.getStatus() == HttpStatus.NOT_ACCEPTABLE

    @Test
    def a_filter_runs_on_its_executor_and_the_declaration_continues_with_and(self):
        audited = self.client.toBlocking().exchange(HttpRequest.GET("/audit/1"), String)
        assert audited.header("X-Audited") == "true"
        body = audited.body()
        assert body != "audited on none"
        assert "EventLoop" not in body
