from typing import Annotated

from micronaut.context.annotation import Requires
from micronaut.http import HttpRequest
from micronaut.http.annotation import Controller, Get
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client

import java

from .AsyncGreeter import AsyncGreeter
from .BackendClient import BackendClient

String = java.type("java.lang.String")


# Constructor injection on a bean with async methods: the event-loop context needs its own instance
# of the controller, created with the same dependencies.
@Requires(property="spec.name", value="PythonAsyncioSpec")
@Controller("/async-constructor")
class AsyncConstructorController:

    def __init__(self,
                 client: BackendClient,
                 greeter: AsyncGreeter,
                 http_client: Annotated[HttpClient, Client("/")],
                 suffix: str = "!"):
        self.client = client
        self.greeter = greeter
        self.http_client = http_client
        self.suffix = suffix
        self.label = "constructor"
        self.calls = []

    @Get("/message")
    async def message(self) -> str:
        self.calls.append("message")
        return f"{self.label}:{await self.client.message()}{self.suffix}"

    @Get("/greeting")
    async def greeting(self) -> str:
        return await self.greeter.greet(self.label) + self.suffix

    @Get("/exchange")
    async def exchange(self) -> str:
        response = await self.http_client.exchange(HttpRequest.GET("/async-backend/message"), String)
        return f"{self.label}:{response.body()}"
