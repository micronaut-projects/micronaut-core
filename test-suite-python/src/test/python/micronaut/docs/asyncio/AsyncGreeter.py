import asyncio
from typing import Annotated

from jakarta.inject import Inject, Singleton
from micronaut.context.annotation import Requires

from .BackendClient import BackendClient


# A Python bean with async methods, awaited by the async controllers below from their event-loop contexts.
# None of its methods is bridged to Java.
@Requires(property="spec.name", value="PythonAsyncioSpec")
@Singleton
class AsyncGreeter:
    client: Annotated[BackendClient, Inject]

    def __init__(self):
        self.greeting = "hello"

    async def greet(self, name: str) -> str:
        await asyncio.sleep(0)
        return f"{self.greeting} {name}"

    async def backend_greeting(self) -> str:
        return f"{self.greeting} {await self.client.message()}"
