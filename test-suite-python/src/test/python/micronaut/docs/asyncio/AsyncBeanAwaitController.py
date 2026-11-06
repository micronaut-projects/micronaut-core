from typing import Annotated

from jakarta.inject import Inject
from micronaut.context.annotation import Requires
from micronaut.http.annotation import Controller, Get

from .AsyncGreeter import AsyncGreeter


@Requires(property="spec.name", value="PythonAsyncioSpec")
@Controller("/async-bean-await")
class AsyncBeanAwaitController:
    greeter: Annotated[AsyncGreeter, Inject]

    # tag::awaitPythonBean[]
    @Get("/greeting")
    async def greeting(self) -> str:
        return await self.greeter.greet("attribute")
    # end::awaitPythonBean[]

    @Get("/backend-greeting")
    async def backend_greeting(self) -> str:
        return await self.greeter.backend_greeting()
