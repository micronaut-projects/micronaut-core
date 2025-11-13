# tag::imports[]
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.runtime.server import EmbeddedServer
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test
from jakarta.inject import Inject
from typing import Annotated
# end::imports[]

# tag::class[]
@MicronautTest # <1>
class HelloClientSpec:
    def __init__(self, server : EmbeddedServer, client : Annotated[HttpClient, Client("/")]):
        self.server = server
        self.client = client

    @Test
    def test_hello_world_response(self) -> None:
        response = self.client.toBlocking().retrieve("/hello") # <3>
        assert response == "Hello World" # <4>
# end::class[]
