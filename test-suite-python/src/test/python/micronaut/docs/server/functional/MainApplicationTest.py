import java
from micronaut.http import HttpRequest
from micronaut.http.client import HttpClient
from micronaut.http.server.netty.configuration import NettyHttpServerConfiguration
from micronaut.runtime.server import EmbeddedServer
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .MainApplication import start, start_on_netty, start_with_beans

Map = java.type("java.util.Map")

ARGS = ["-micronaut.server.port=-1", "-spec.name=MainApplicationTest"]


def client(context):
    return context.createBean(HttpClient, context.getBean(EmbeddedServer).getURL())


@MicronautTest
class MainApplicationTest:

    @Test
    def routes_in_the_main_function(self):
        context = start(*ARGS)
        try:
            http = client(context)
            assert http.toBlocking().retrieve(HttpRequest.GET("/hello/World")) == "Hello World"
            http.close()
        finally:
            context.close()

    @Test
    def routes_that_use_beans(self):
        context = start_with_beans(*ARGS)
        try:
            http = client(context)
            saved = http.toBlocking().retrieve(HttpRequest.POST("/items", Map.of("id", 0, "name", "Pen")), Map)
            found = http.toBlocking().retrieve(HttpRequest.GET(f"/items/{saved.get('id')}"), Map)
            assert found.get("name") == "Pen"
            http.close()
        finally:
            context.close()

    @Test
    def the_chosen_server_with_its_configuration(self):
        context = start_on_netty(*ARGS)
        try:
            http = client(context)
            assert http.toBlocking().retrieve(HttpRequest.GET("/ping")) == "pong"
            assert context.getBean(NettyHttpServerConfiguration).getMaxHeaderSize() == 16384
            http.close()
        finally:
            context.close()
