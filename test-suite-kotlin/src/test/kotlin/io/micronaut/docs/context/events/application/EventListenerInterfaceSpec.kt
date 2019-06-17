package io.micronaut.docs.context.events.application

import io.kotlintest.shouldBe
import io.kotlintest.specs.AnnotationSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer

internal class EventListenerInterfaceSpec : AnnotationSpec() {

    lateinit var server: EmbeddedServer

    @Before
    fun setup() {
        server = ApplicationContext.run(EmbeddedServer::class.java,
                mapOf("micronaut.application.name" to "listener test",
                        "micronaut.server.port" to "-1"))
    }

    @After
    fun cleanup() {
        server.close()
    }

    @Test
    fun testEventListenerWasNotified() {
        val listener = server.applicationContext.getBean(DoOnStartup::class.java)

        listener.invocationCounter.shouldBe(1)
    }
}
