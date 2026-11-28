package io.micronaut.docs.http.client.retry

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InventoryClientTest {

    @Test
    fun theExchangeIsRetried() {
        ApplicationContext.run(EmbeddedServer::class.java, mapOf<String, Any>("spec.name" to "InventoryClientTest")).use { server ->
            ApplicationContext.run(mapOf(
                "micronaut.retry.policies.inventory.delay" to "10ms",
                "micronaut.http.services.inventory.url" to server.url.toString(),
                "micronaut.http.services.inventory.retry.enabled" to true,
                "micronaut.http.services.inventory.retry.policy" to "inventory"
            )).use { ctx ->
                InventoryController.CALLS.set(0)
                assertEquals("42", ctx.getBean(InventoryClient::class.java).stock("apples"))
                assertEquals(2, InventoryController.CALLS.get())
            }
        }
    }
}
