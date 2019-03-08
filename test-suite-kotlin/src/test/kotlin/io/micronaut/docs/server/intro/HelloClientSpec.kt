package io.micronaut.docs.server.intro

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.runtime.server.EmbeddedServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * @author graemerocher
 * @since 1.0
 */
// tag::class-init[]
class HelloClientSpec {

    lateinit var server: EmbeddedServer
    lateinit var client: HelloClient

    @BeforeTest
    fun setup() {
        // end::class-init[]
        server = ApplicationContext.run(EmbeddedServer::class.java, mapOf("spec.name" to HelloControllerSpec::class.simpleName), Environment.TEST)

        /*
// tag::embeddedServer[]
        server = ApplicationContext.run(EmbeddedServer::class.java) // <1>
// end::embeddedServer[]
        */
        //tag::class-end[]
        client = server!!
                .applicationContext
                .getBean(HelloClient::class.java)// <2>
    }

    @AfterTest
    fun teardown() {
        server?.close()
    }

    @Test
    fun testHelloWorldResponse() {
        assertEquals("Hello World", client.hello().blockingGet())// <3>
    }
}
// end::class-end[]
