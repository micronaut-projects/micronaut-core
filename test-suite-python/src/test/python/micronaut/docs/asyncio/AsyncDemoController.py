from typing import Annotated

import asyncio
import builtins
import java
from jakarta.inject import Inject
from micronaut.context.annotation import Requires
from micronaut.http.annotation import Controller, Get

from .BackendClient import BackendClient

System = java.type("java.lang.System")
Thread = java.type("java.lang.Thread")
TimeUnit = java.type("java.util.concurrent.TimeUnit")


@Requires(property="spec.name", value="PythonAsyncioSpec")
@Controller("/async-demo")
class AsyncDemoController:
    client: Annotated[BackendClient, Inject]

    # tag::awaitClient[]
    @Get("/message")
    async def message(self) -> str:
        return "demo:" + await self.client.message()
    # end::awaitClient[]

    # tag::awaitPublisher[]
    @Get("/publisher-message")
    async def publisher_message(self) -> str:
        return "demo:" + await self.client.publisher_message()
    # end::awaitPublisher[]

    @Get("/probe")
    async def probe(self) -> str:
        loop = asyncio.get_running_loop()
        before_thread = Thread.currentThread().getName()
        started = System.nanoTime()
        heartbeat = loop.create_future()

        def beat():
            elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            heartbeat.set_result(elapsed)

        loop.call_later(0.05, beat)
        response = await self.client.message()
        heartbeat_elapsed = await heartbeat
        after_thread = Thread.currentThread().getName()
        return f"{before_thread}|{after_thread}|{heartbeat_elapsed}|{response}"

    @Get("/context-id")
    async def context_id(self) -> str:
        return builtins.__MN_CTX_ID__
