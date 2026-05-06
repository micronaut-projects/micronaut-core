from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Requires
# tag::imports[]
from micronaut.http import HttpRequest, HttpStatus, MediaType
from micronaut.http.annotation import Body, Controller, Get, Post, Status
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client

from .Message import Message

Mono = java.type("reactor.core.publisher.Mono")
Publisher = java.type("org.reactivestreams.Publisher")
# end::imports[]


@Requires(property="spec.name", value="HelloControllerSpec")
@Controller("/")
class HelloController:
    httpClient: Annotated[HttpClient, Inject, Client("/endpoint")]

    # tag::nonblocking[]
    @Get("/hello/{name}")
    # TODO: Re-enable @SingleResult when Python can import io.micronaut.core.async.annotation.SingleResult.
    # @SingleResult
    def hello(self, name: str) -> Publisher:  # <1>
        return getattr(Mono, "from")(
            self.httpClient.retrieve(HttpRequest.GET("/hello/" + name))
        )  # <2>
    # end::nonblocking[]

    @Get("/endpoint/hello/{name}")
    def helloEndpoint(self, name: str) -> str:
        return "Hello " + name

    # tag::json[]
    @Get("/greet/{name}")
    def greet(self, name: str) -> Message:
        return Message("Hello " + name)
    # end::json[]

    @Status(HttpStatus.CREATED)
    @Post("/greet")
    def echo(self, message: Annotated[Message, Body]) -> Message:
        return message

    @Status(HttpStatus.CREATED)
    @Post(value="/hello", consumes=MediaType.TEXT_PLAIN, produces=MediaType.TEXT_PLAIN)
    def echoHello(self, message: Annotated[str, Body]) -> str:
        return message
