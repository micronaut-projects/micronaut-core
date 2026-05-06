from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Disabled, Test

Cookie = java.type("io.micronaut.http.cookie.Cookie")
HashSet = java.type("java.util.HashSet")
HttpClientResponseException = java.type("io.micronaut.http.client.exceptions.HttpClientResponseException")
HttpRequest = java.type("io.micronaut.http.HttpRequest")
HttpStatus = java.type("io.micronaut.http.HttpStatus")


@MicronautTest
class BindingControllerTest:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def test_cookie_binding(self):
        body = self.client.toBlocking().retrieve(
            HttpRequest.GET("/binding/cookieName").cookie(Cookie.of("myCookie", "cookie value"))
        )

        assert body is not None
        assert body == "cookie value"

        body = self.client.toBlocking().retrieve(
            HttpRequest.GET("/binding/cookieInferred").cookie(Cookie.of("myCookie", "cookie value"))
        )

        assert body is not None
        assert body == "cookie value"

    @Test
    def test_cookies_binding(self):
        cookies = HashSet()
        cookies.add(Cookie.of("myCookieA", "cookie A value"))
        cookies.add(Cookie.of("myCookieB", "cookie B value"))

        body = self.client.toBlocking().retrieve(HttpRequest.GET("/binding/cookieMultiple").cookies(cookies))

        assert body is not None
        assert body == "[\"cookie A value\",\"cookie B value\"]"

    @Test
    def test_header_binding(self):
        body = self.client.toBlocking().retrieve(HttpRequest.GET("/binding/headerName").header("Content-Type", "test"))

        assert body is not None
        assert body == "test"

        body = self.client.toBlocking().retrieve(HttpRequest.GET("/binding/headerInferred").header("Content-Type", "test"))

        assert body is not None
        assert body == "test"

    @Test
    @Disabled("Python nullable @Header binding returns a non-Java-equivalent status for a missing header")
    def test_header_nullable_binding(self):
        try:
            self.client.toBlocking().retrieve(HttpRequest.GET("/binding/headerNullable"))
            assert False, "Expected missing nullable header route to fail"
        except HttpClientResponseException as e:
            assert e.getResponse().getStatus() == HttpStatus.NOT_FOUND

    @Test
    @Disabled("GraalPy ForeignDateTime string conversion fails with datetime moduleData null")
    def test_header_date_binding(self):
        body = self.client.toBlocking().retrieve(
            HttpRequest.GET("/binding/date").header("date", "Tue, 3 Jun 2008 11:05:30 GMT")
        )

        assert body is not None
        assert body == "2008-06-03T11:05:30Z"

        body = self.client.toBlocking().retrieve(
            HttpRequest.GET("/binding/dateFormat").header("date", "03/06/2008 11:05:30 AM GMT")
        )

        assert body is not None
        assert body == "2008-06-03T11:05:30Z[GMT]"
