package io.micronaut.docs.http.client.bind.annotation

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.*

class AnnotationBinderSpec {

    @Test
    fun testBindingToTheRequest() {
        val server = ApplicationContext.run(EmbeddedServer::class.java)
        val client = server.applicationContext.getBean(MetadataClient::class.java)

        val metadata: MutableMap<String, Any> = LinkedHashMap()
        metadata["version"] = 3.6
        metadata["deploymentId"] = 42L
        val resp = client.get(metadata)
        Assertions.assertEquals("3.6", resp)
        server.close()
    }
}
