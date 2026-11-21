package io.micronaut.docs.http.client.retry

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals

class InventoryClientTest {

    @Test
    void theExchangeIsRetried() {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'InventoryClientTest']);
             ApplicationContext ctx = ApplicationContext.run(
                 'micronaut.retry.policies.inventory.delay': '10ms',
                 'micronaut.http.services.inventory.url': server.URL.toString(),
                 'micronaut.http.services.inventory.retry.enabled': true,
                 'micronaut.http.services.inventory.retry.policy': 'inventory')) {
            InventoryController.CALLS.set(0)
            assertEquals('42', ctx.getBean(InventoryClient).stock('apples'))
            assertEquals(2, InventoryController.CALLS.get())
        }
    }
}
