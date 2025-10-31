package io.micronaut.management.env

import com.fasterxml.jackson.databind.ObjectMapper
import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.http.client.HttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EnvironmentEndpointKeysFilterTest {

    @Test
    fun `env endpoint respects endpoints_env_keys filter`() {
        val props = mapOf(
            // isolate config for this spec
            "spec.name" to "EnvironmentEndpointKeysFilterTest",
            // ensure server starts on random port
            "micronaut.server.port" to -1,
            // enable env endpoint and make it non-sensitive so it's accessible in test
            "endpoints.all.enabled" to true,
            "endpoints.all.sensitive" to false,
            "endpoints.env.enabled" to true,
            "endpoints.env.sensitive" to false,
            // proposed configuration: expose only activeEnvironments
            "endpoints.env.keys[0]" to "activeEnvironments"
        )

        val server = ApplicationContext.run(EmbeddedServer::class.java, props)
        val client = HttpClient.create(server.url)
        try {
            val json = client.toBlocking().retrieve("/env")
            val mapper = server.applicationContext.getBean(ObjectMapper::class.java)
            val node = mapper.readTree(json)

            assertTrue(node.has("activeEnvironments"), "Should contain 'activeEnvironments'")
            assertFalse(node.has("packages"), "Should not contain 'packages' when filtered")
            assertFalse(node.has("propertySources"), "Should not contain 'propertySources' when filtered")
            assertEquals(1, node.size(), "Only 'activeEnvironments' should be present when filtered via endpoints.env.keys")
        } finally {
            client.close()
            server.close()
        }
    }
}
