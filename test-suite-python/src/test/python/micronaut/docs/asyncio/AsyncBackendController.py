import asyncio
import java

from micronaut.context.annotation import Requires
from micronaut.core.async_.annotation import SingleResult
from micronaut.http.annotation import Controller, Get

Mono = java.type("reactor.core.publisher.Mono")
Publisher = java.type("org.reactivestreams.Publisher")
AsyncioConcurrentClientRunner = java.type("micronaut.docs.asyncio.AsyncioConcurrentClientRunner")


@Requires(property="spec.name", value="PythonAsyncioSpec")
@Controller("/async-backend")
class AsyncBackendController:

    # tag::backend[]
    @Get("/message")
    async def message(self) -> str:
        await asyncio.sleep(0.1)
        return "backend"
    # end::backend[]

    @Get("/concurrent-message")
    async def concurrent_message(self) -> str:
        AsyncioConcurrentClientRunner.enterActive()
        try:
            await asyncio.sleep(0.1)
            return "backend"
        finally:
            AsyncioConcurrentClientRunner.exitActive()

    # tag::publisherBackend[]
    @Get("/publisher-message")
    @SingleResult
    def publisher_message(self) -> Publisher[str]:
        return Mono.just("publisher-backend")
    # end::publisherBackend[]

    @Get("/reset-stats")
    def reset_stats(self) -> str:
        AsyncioConcurrentClientRunner.resetActive()
        return "ok"

    @Get("/max-active")
    def max_active(self) -> str:
        return str(AsyncioConcurrentClientRunner.maxActive())
