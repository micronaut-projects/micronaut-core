from typing import Annotated, AsyncIterator

import asyncio
import java
from jakarta.inject import Inject
from micronaut.context.annotation import Requires
from micronaut.http import MediaType
from micronaut.http.annotation import Controller, Get
from micronaut.http.sse import Event

# tag::imports[]
from micronaut_asyncio import as_async_iterable, as_publisher
# end::imports[]

from .Tick import Tick
from .TickClient import TickClient

Publisher = java.type("org.reactivestreams.Publisher")
StreamProbe = java.type("micronaut.docs.asyncio.streaming.StreamProbe")

_gate = None


@Requires(property="spec.name", value="PythonStreamingSpec")
@Controller("/async-streams")
class StreamController:
    client: Annotated[TickClient, Inject]

    # tag::explicit[]
    @Get(value="/ticks", produces=MediaType.APPLICATION_JSON_STREAM)
    def ticks(self) -> Publisher[Tick]:
        async def generate():  # <1>
            for index in range(3):
                await asyncio.sleep(0.01)  # <2>
                yield Tick(index=index, label=f"tick-{index}")

        return as_publisher(generate)  # <3>
    # end::explicit[]

    # tag::generator[]
    @Get(value="/events", produces=MediaType.TEXT_EVENT_STREAM)
    async def events(self) -> AsyncIterator[Event[Tick]]:  # <1>
        for index in range(3):
            await asyncio.sleep(0.01)
            yield Event.of(Tick(index=index, label=f"event-{index}"))  # <2>
    # end::generator[]

    # tag::consume[]
    @Get("/consume")
    async def consume(self) -> str:
        labels = []
        async with as_async_iterable(self.client.counted()) as ticks:  # <1>
            async for tick in ticks:  # <2>
                labels.append(tick.label)
                if len(labels) == 3:
                    break  # <3>
        return ",".join(labels)
    # end::consume[]

    @Get(value="/counted", produces=MediaType.APPLICATION_JSON_STREAM)
    async def counted(self) -> AsyncIterator[Tick]:
        StreamProbe.started("counted")
        try:
            for index in range(10):
                yield Tick(index=index, label=f"counted-{index}")
                await asyncio.sleep(0.005)
        finally:
            StreamProbe.finished("counted")

    @Get(value="/endless", produces=MediaType.APPLICATION_JSON_STREAM)
    async def endless(self) -> AsyncIterator[Tick]:
        StreamProbe.started("endless")
        index = 0
        try:
            while True:
                yield Tick(index=index, label=f"endless-{index}")
                index += 1
                await asyncio.sleep(0.005)
        finally:
            StreamProbe.finished("endless")

    @Get(value="/gated", produces=MediaType.APPLICATION_JSON_STREAM)
    async def gated(self) -> AsyncIterator[Tick]:
        global _gate
        yield Tick(index=0, label="before-gate")
        _gate = asyncio.get_running_loop().create_future()
        StreamProbe.awaitingGate(True)
        try:
            await _gate
        finally:
            StreamProbe.awaitingGate(False)
        yield Tick(index=1, label="after-gate")

    @Get("/release")
    def release(self) -> str:
        gate = _gate
        if gate is not None and not gate.done():
            gate.set_result(True)
            return "released"
        return "nothing to release"

    @Get(value="/failing", produces=MediaType.APPLICATION_JSON_STREAM)
    async def failing(self) -> AsyncIterator[Tick]:
        yield Tick(index=0, label="only")
        raise ValueError("generator failed")

    @Get(value="/strings", produces=MediaType.APPLICATION_JSON_STREAM)
    async def strings(self) -> AsyncIterator[str]:
        for value in ("a", "b", "c"):
            yield value
