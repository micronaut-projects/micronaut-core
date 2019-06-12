package io.micronaut.docs.server.intro

import io.kotlintest.shouldBe
import io.kotlintest.specs.AnnotationSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.runtime.server.EmbeddedServer

/**
 * @author graemerocher
 * @since 1.0
 */
// tag::class-init[]
class HelloClientSpec : AnnotationSpec() {

    lateinit var server: EmbeddedServer
    lateinit var client: HelloClient

    @BeforeEach
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

    @AfterEach
    fun teardown() {
        server?.close()
    }

    @Test
    fun testHelloWorldResponse() {
        client.hello().blockingGet().shouldBe("Hello World")// <3>
    }
}
// end::class-end[]
