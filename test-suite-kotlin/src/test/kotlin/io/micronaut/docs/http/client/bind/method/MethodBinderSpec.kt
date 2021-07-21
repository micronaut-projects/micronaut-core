package io.micronaut.docs.http.client.bind.method;

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MethodBinderSpec {

    @Test
    fun testBindingToTheRequest() {
        val server = ApplicationContext.run(EmbeddedServer::class.java)
        val client = server.applicationContext.getBean(NameAuthorizedClient::class.java)

        val resp = client.get()
        Assertions.assertEquals("Hello, Bob", resp)

        server.close()
    }
}
