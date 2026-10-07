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


@Property(name="spec.name", value="StaticResourceRoutesTest")
@Property(name="site.directory", value="src/test/resources/functional-static/site")
@MicronautTest
class StaticResourceRoutesTest:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def the_routes_serve_static_resources(self):
        http = self.client.toBlocking()

        css = http.exchange(HttpRequest.GET("/assets/css/site.css"), String)
        assert css.getStatus() == HttpStatus.OK
        assert css.body() == "body { color: teal; }\n"
        assert css.getContentType().orElseThrow().getName() == "text/css"
        assert css.getHeaders().get("X-Assets") == "true"
        assert css.getHeaders().get("Cache-Control") == "public, max-age=31536000, immutable"

        hello = http.exchange(HttpRequest.GET("/site/hello.txt"), String)
        assert hello.getStatus() == HttpStatus.OK
        assert hello.body() == "Hello from the file system\n"
        assert hello.getContentType().orElseThrow().getName() == "text/plain"

        # the index file at the prefix of the group, with the content type of the file, not of the group
        manual = http.exchange(HttpRequest.GET("/manual"), String)
        assert manual.getStatus() == HttpStatus.OK
        assert manual.body() == "<h1>Manual</h1>\n"
        assert manual.getContentType().orElseThrow().getName() == "text/html"
        assert manual.getHeaders().get("X-Manual") == "true"

        # a missing file, and paths that try to leave the directory, are not found
        for path in ["/assets/missing.css", "/assets/../secret.txt", "/assets/%2e%2e/secret.txt",
                     "/assets/..%2fsecret.txt", "/site/..%2f..%2fsecret.txt"]:
            try:
                http.exchange(HttpRequest.GET(path), String)
                raise AssertionError("the request did not fail: " + path)
            except HttpClientResponseException as e:
                assert e.getStatus() == HttpStatus.NOT_FOUND, path
