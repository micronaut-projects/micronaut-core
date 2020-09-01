package io.micronaut.docs.http.client.bind.type

import io.micronaut.context.ApplicationContext
import io.micronaut.docs.http.client.bind.Metadata
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class CustomBinderSpec {

    @Test
    fun testBindingToTheRequest() {
        val server = ApplicationContext.run(EmbeddedServer::class.java)
        val client = server.applicationContext.getBean(MetadataClient::class.java)
        val resp = client.get(Metadata(3.6, 42L))
        Assertions.assertEquals("3.6", resp)
        server.close()
    }
}
