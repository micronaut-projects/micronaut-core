import asyncio
from typing import Annotated, AsyncIterator

from jakarta.inject import Inject, Singleton
from micronaut.context.annotation import Requires
from micronaut.http import MediaType
from micronaut.http.annotation import Controller, Get

from .BackendClient import BackendClient

# An explicit scope replaces the ContextPooled scope route modules get by default: the module is a
# singleton of the startup context and its async routes run on its import in an event-loop context
Controller("/singleton-async-routes")
Singleton()
Requires(property="spec.name", value="PythonAsyncioSpec")

backend_client: Annotated[BackendClient, Inject]

greeting = "initial"


@Get("/message")
async def singleton_message() -> str:
    return "singleton:" + await backend_client.message()


@Get(value="/stream", produces=MediaType.APPLICATION_JSON_STREAM)
async def singleton_stream() -> AsyncIterator[str]:
    yield "first"
    await asyncio.sleep(0)
    yield await backend_client.message()


@Get("/greeting/{value}")
def set_greeting(value: str) -> str:
    global greeting
    greeting = value
    return greeting


@Get("/greeting")
async def async_greeting() -> str:
    await asyncio.sleep(0)
    return greeting
