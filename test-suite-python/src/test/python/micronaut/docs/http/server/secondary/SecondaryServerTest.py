from typing import Annotated

from jakarta.inject import Inject, Named
from micronaut.context.annotation import Property
from micronaut.core.util import StringUtils
from micronaut.http import HttpRequest
from micronaut.http.annotation import Controller, Get
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.runtime.server import EmbeddedServer
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Disabled, Test

from .SecondaryNettyServer import SecondaryNettyServer


@MicronautTest
@Property(name="secondary.enabled", value=StringUtils.TRUE)
@Property(name="micronaut.http.client.ssl.insecure-trust-all-certificates", value=StringUtils.TRUE)
@Disabled("Secondary Python factory starts an eager NettyEmbeddedServer before the GraalPy context is initialized")
class SecondaryServerTest:
    # tag::inject[]
    httpClient: Annotated[
        HttpClient, Client(path="/", id="another"), Inject
    ]  # <1>

    embeddedServer: Annotated[
        EmbeddedServer, Named("another"), Inject
    ]  # <2>
    # end::inject[]

    @Test
    def testCallSecondaryServer(self):
        result = self.httpClient.toBlocking().retrieve("/test/secondary/server")
        assert result.endswith(str(self.embeddedServer.getPort()))
        assert self.embeddedServer.getScheme().lower() == "https"


@Controller("/test/secondary/server")
class TestController:
    @Get
    def hello(self, request: HttpRequest) -> str:
        return "Hello from: " + str(request.getServerAddress().getPort())
