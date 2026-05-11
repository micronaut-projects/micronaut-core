from typing import Annotated

import java
from jakarta.inject import Inject, Singleton
from micronaut.context.annotation import Property, Requires, Value
from micronaut.http import HttpRequest, MutableHttpRequest
from micronaut.http.annotation import ClientFilter, Controller, Get, Header, RequestFilter
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

Flux = java.type("reactor.core.publisher.Flux")
String = java.type("java.lang.String")

token = "XXXX"
username = "john"


# tag::bintrayApiConstants[]
class BintrayApi:
    URL = "https://api.bintray.com"
# end::bintrayApiConstants[]

BintrayApi.URL = "/"


@Requires(property="spec.name", value="ThirdPartyClientFilterSpec")
# tag::bintrayService[]
@Singleton
class BintrayService:
    client: Annotated[HttpClient, Inject, Client(BintrayApi.URL)]  # <1>
    org: Annotated[str, Value("${bintray.organization}")]  # <2>

    def fetchRepositories(self):
        return getattr(Flux, "from")(
            self.client.exchange(HttpRequest.GET("/repos/" + self.org), String)
        )  # <2>

    def fetchPackages(self, repo: str):
        return getattr(Flux, "from")(
            self.client.exchange(HttpRequest.GET("/repos/" + self.org + "/" + repo + "/packages"), String)
        )  # <2>
# end::bintrayService[]


@Requires(property="spec.name", value="ThirdPartyClientFilterSpec")
# tag::bintrayFilter[]
@ClientFilter("/repos/**")  # <1>
class BintrayFilter:
    username: Annotated[str, Value("${bintray.username}")]  # <2>
    token: Annotated[str, Value("${bintray.token}")]  # <2>

    @RequestFilter
    def filter(self, request: MutableHttpRequest) -> None:
        request.basicAuth(self.username, self.token)  # <3>
# end::bintrayFilter[]


@Requires(property="spec.name", value="ThirdPartyClientFilterSpec")
@Controller("/repos")
class HeaderController:

    @Get("/grails")
    def echoAuthorization(self, authorization: Annotated[str, Header]) -> str:
        return authorization


@Property(name="spec.name", value="ThirdPartyClientFilterSpec")
@Property(name="bintray.username", value="john")
@Property(name="bintray.token", value="XXXX")
@Property(name="bintray.organization", value="grails")
@MicronautTest
class ThirdPartyClientFilterSpec:
    bintrayService: Annotated[BintrayService, Inject]

    @Test
    def aClientFilterIsAppliedToTheRequestAndAddsTheAuthorizationHeader(self) -> None:
        result = self.bintrayService.fetchRepositories().blockFirst().body()

        Base64 = java.type("java.util.Base64")
        expected = "Basic " + Base64.getEncoder().encodeToString((username + ":" + token).encode())
        assert result == expected
