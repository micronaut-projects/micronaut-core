package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KotlinInheritedDefaultClientSpec {
    @Test
    fun testInheritedDefaultMethodOnClient() {
        val server = ApplicationContext.run(
            EmbeddedServer::class.java,
            mapOf("spec.name" to "KotlinInheritedDefaultClientSpec")
        )
        server.use {
            val client = server.applicationContext.getBean(KotlinInheritedDefaultClient::class.java)
            assertEquals("success from default", client.inheritedDefault())
        }
    }
}
