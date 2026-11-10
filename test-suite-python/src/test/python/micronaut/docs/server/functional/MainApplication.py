# tag::imports[]
import sys

from micronaut.http import HttpResponse, MediaType
from micronaut.http.server import HttpApplication
from micronaut.http.server.netty import NettyEmbeddedServer
from micronaut.http.server.netty.configuration import NettyHttpServerConfiguration
from micronaut.web.router.builder import HttpRouteBuilder

from .Item import Item
from .ItemRepository import ItemRepository
# end::imports[]

# Routes declared in the main function. Each example returns the context, so that the test can stop it.


# tag::main[]
def start(*args: str):
    def routes(routes: HttpRouteBuilder) -> None:
        routes.GET("/hello/{name}", lambda request, path_variables:  # <1>
                   HttpResponse.ok("Hello " + path_variables.getString("name")).contentType(MediaType.TEXT_PLAIN_TYPE))

    return HttpApplication.run(list(args), routes)


if __name__ == "__main__":
    start(*sys.argv[1:])
# end::main[]


# tag::beans[]
def start_with_beans(*args: str):
    def routes(routes: HttpRouteBuilder, beans) -> None:  # <1>
        items = beans.getBean(ItemRepository)  # <2>
        routes.GET("/items/{id}", lambda request, path_variables:
                   HttpResponse.ok(items.find(path_variables.getLong("id"))))
        (routes.POST("/items")
            .body(Item)
            .handle(lambda request, path_variables, item: HttpResponse.created(items.save(item))))

    return HttpApplication.run(list(args), routes)
# end::beans[]


# tag::server[]
def start_on_netty(*args: str):
    def configure(netty) -> None:
        netty.setMaxHeaderSize(16384)

    return (HttpApplication.build(*args)  # <1>
            .server(NettyEmbeddedServer)  # <2>
            .configure(NettyHttpServerConfiguration, configure)  # <3>
            .property("micronaut.server.idle-timeout", "5m")  # <4>
            .routes(lambda routes: routes.GET("/ping", lambda request, path_variables:
                                              HttpResponse.ok("pong").contentType(MediaType.TEXT_PLAIN_TYPE)))
            .start())
# end::server[]
