package io.micronaut.docs.http.client.retry

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import java.util.concurrent.atomic.AtomicInteger

/**
 * Answers 503 to the first request, then the stock.
 */
@Requires(property = "spec.name", value = "InventoryClientTest")
@Controller("/inventory")
class InventoryController {

    @Get("/{item}")
    fun stock(item: String): HttpResponse<String> =
        if (CALLS.incrementAndGet() == 1) HttpResponse.status(HttpStatus.SERVICE_UNAVAILABLE) else HttpResponse.ok("42")

    companion object {
        val CALLS = AtomicInteger()
    }
}
