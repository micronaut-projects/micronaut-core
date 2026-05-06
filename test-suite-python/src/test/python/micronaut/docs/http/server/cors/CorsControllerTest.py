from typing import Annotated

from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http import HttpHeaders, HttpMethod, HttpRequest, MediaType
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.http.client.exceptions import HttpClientResponseException
from micronaut.http.uri import UriBuilder
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test


@Property(name="spec.name", value="CorsControllerSpec")
@MicronautTest
class CorsControllerTest:
    httpClient: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def crossOriginWithAllowedOrigin(self):
        request = self.preflight(UriBuilder.of("/hello"), "https://myui.com", HttpMethod.GET)
        self.httpClient.toBlocking().exchange(request)

    @Test
    def crossOriginWithNotAllowedOrigin(self):
        request = self.preflight(UriBuilder.of("/hello"), "https://google.com", HttpMethod.GET)

        try:
            self.httpClient.toBlocking().exchange(request)
            assert False
        except HttpClientResponseException:
            pass

    def preflight(self, uriBuilder, originValue: str, method: HttpMethod):
        return (
            HttpRequest.OPTIONS(uriBuilder.build())
            .header(HttpHeaders.ACCEPT, MediaType.TEXT_PLAIN)
            .header(HttpHeaders.ORIGIN, originValue)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method)
        )
