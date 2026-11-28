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
@Requires(property = 'spec.name', value = 'InventoryClientTest')
@Controller('/inventory')
class InventoryController {
    static final AtomicInteger CALLS = new AtomicInteger()

    @Get('/{item}')
    HttpResponse<String> stock(String item) {
        CALLS.incrementAndGet() == 1 ? HttpResponse.status(HttpStatus.SERVICE_UNAVAILABLE) : HttpResponse.ok('42')
    }
}
