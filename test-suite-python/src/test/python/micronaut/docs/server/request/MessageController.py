import java

# tag::imports[]
from micronaut.http import HttpRequest, HttpResponse
from micronaut.http.annotation import Controller, Get
from micronaut.http.context import ServerRequestContext
# end::imports[]

Mono = java.type("reactor.core.publisher.Mono")
RuntimeException = java.type("java.lang.RuntimeException")


# tag::class[]
@Controller("/request")
class MessageController:
# end::class[]

    # tag::request[]
    @Get("/hello")  # <1>
    def hello(self, request: HttpRequest) -> HttpResponse:
        name = request.getParameters().getFirst("name").orElse("Nobody")  # <2>

        return HttpResponse.ok("Hello " + name + "!!").header("X-My-Header", "Foo")  # <3>
    # end::request[]

    # tag::static-request[]
    @Get("/hello-static")  # <1>
    def helloStatic(self) -> HttpResponse:
        request = ServerRequestContext.currentRequest().orElseThrow(  # <1>
            lambda: RuntimeException("No request present")
        )
        name = request.getParameters().getFirst("name").orElse("Nobody")

        return HttpResponse.ok("Hello " + name + "!!").header("X-My-Header", "Foo")
    # end::static-request[]

    # tag::request-context[]
    @Get("/hello-reactor")
    def helloReactor(self) -> Mono:
        def response(ctx):  # <1>
            request = ctx.get(ServerRequestContext.KEY)  # <2>
            name = request.getParameters().getFirst("name").orElse("Nobody")

            return Mono.just(HttpResponse.ok("Hello " + name + "!!").header("X-My-Header", "Foo"))

        return Mono.deferContextual(response)
    # end::request-context[]
# tag::endclass[]
# end::endclass[]
