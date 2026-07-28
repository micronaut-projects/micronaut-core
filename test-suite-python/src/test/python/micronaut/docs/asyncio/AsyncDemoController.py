from typing import Annotated

import asyncio
import builtins
import java
from jakarta.inject import Inject
from micronaut.context.annotation import Requires
from micronaut.http import HttpRequest
from micronaut.http.annotation import Controller, Get
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client

from .BackendClient import BackendClient

String = java.type("java.lang.String")
System = java.type("java.lang.System")
Thread = java.type("java.lang.Thread")
TimeUnit = java.type("java.util.concurrent.TimeUnit")


@Requires(property="spec.name", value="PythonAsyncioSpec")
@Controller("/async-demo")
class AsyncDemoController:
    client: Annotated[BackendClient, Inject]
    # tag::httpClientInjection[]
    http_client: Annotated[HttpClient, Inject, Client("/")]
    # end::httpClientInjection[]

    # tag::awaitClient[]
    @Get("/message")
    async def message(self) -> str:
        return "demo:" + await self.client.message()
    # end::awaitClient[]

    @Get("/concurrent-message")
    async def concurrent_message(self) -> str:
        return "demo:" + await self.client.concurrent_message()

    # tag::awaitPublisher[]
    @Get("/publisher-message")
    async def publisher_message(self) -> str:
        return "demo:" + await self.client.publisher_message()
    # end::awaitPublisher[]

    # tag::awaitHttpClientExchange[]
    @Get("/http-client-exchange")
    async def http_client_exchange(self) -> str:
        response = await self.http_client.exchange(HttpRequest.GET("/async-backend/message"), String)
        return "exchange:" + response.body()
    # end::awaitHttpClientExchange[]

    # tag::taskGroup[]
    @Get("/task-group")
    async def task_group(self) -> str:
        async def backend_message() -> str:
            return await self.client.message()

        async def delayed_message() -> str:
            await asyncio.sleep(0.001)
            return "sleep"

        async with asyncio.TaskGroup() as task_group:
            backend_task = task_group.create_task(backend_message())
            sleep_task = task_group.create_task(delayed_message())

        return f"{backend_task.result()}:{sleep_task.result()}"
    # end::taskGroup[]

    @Get("/task-group-cancel")
    async def task_group_cancel(self) -> str:
        cleanup = []
        failed = False

        async def fail() -> None:
            await asyncio.sleep(0)
            raise RuntimeError("boom")

        async def wait_forever() -> None:
            try:
                await asyncio.sleep(10)
            finally:
                cleanup.append("cleanup")

        try:
            async with asyncio.TaskGroup() as task_group:
                task_group.create_task(wait_forever())
                task_group.create_task(fail())
        except* RuntimeError:
            failed = True

        return f"failed={failed}:cleanup={'cleanup' in cleanup}"

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
