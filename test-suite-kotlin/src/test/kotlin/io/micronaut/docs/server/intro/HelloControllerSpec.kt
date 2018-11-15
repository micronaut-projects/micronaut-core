package io.micronaut.docs.server.intro

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import junit.framework.TestCase

class HelloControllerSpec : TestCase() {

    fun testNullableFieldInjection() {
        var embeddedServer:EmbeddedServer= ApplicationContext.run(EmbeddedServer::class.java,
                mapOf("spec.name" to "HelloControllerSpec"),
                Environment.TEST)
        var client : HttpClient = HttpClient.create(embeddedServer.url)
        var rsp : String = client.toBlocking().retrieve("/hello")
        TestCase.assertEquals("Hello World", rsp)
        client.close()
        embeddedServer.close()
    }
}
