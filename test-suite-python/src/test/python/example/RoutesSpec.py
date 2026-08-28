from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.runtime.server import EmbeddedServer
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test
from jakarta.inject import Inject
from typing import Annotated


# tag::class[]
@MicronautTest # <1>
class RoutesSpec:
    server : Annotated[EmbeddedServer, Inject] # <1>
    client : Annotated[HttpClient, Inject, Client("/")] # <2>

    @Test
    def test_hello_world_response(self) -> None:
        response = self.client.toBlocking().retrieve("/route-from-script") # <3>
        assert response == "Hello from Python service!" # <4>
# end::class[]

    @Test
    def test_hello_world_response2(self) -> None:
        response = self.client.toBlocking().retrieve("/route-from-script2") # <3>
        assert response == "Hello from Another Python service!" # <4>


    @Test
    def test_hello_world_response3(self) -> None:
        response = self.client.toBlocking().retrieve("/another-route-from-script") # <3>
        assert response == "Hello from Python service!!" # <4>

