import java

from micronaut.context.annotation import Requires
from micronaut.http import HttpResponse, HttpStatus
from micronaut.http.annotation import Controller, Get

AtomicInteger = java.type("java.util.concurrent.atomic.AtomicInteger")

CALLS = AtomicInteger()


@Requires(property="spec.name", value="InventoryClientTest")
@Controller("/inventory")
class InventoryController:
    """Answers 503 to the first request, then the stock."""

    @Get("/{item}")
    def stock(self, item: str) -> HttpResponse:
        if CALLS.incrementAndGet() == 1:
            return HttpResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
        return HttpResponse.ok("42")
