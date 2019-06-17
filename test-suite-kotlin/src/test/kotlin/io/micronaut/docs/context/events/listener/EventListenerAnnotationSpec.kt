package io.micronaut.docs.context.events.listener

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer

internal class EventListenerAnnotationSpec : StringSpec({

    "test event listener was notified" {
        val server: EmbeddedServer = ApplicationContext.run(EmbeddedServer::class.java)
        val context = server.applicationContext
        val listener = context.getBean(DoOnStartup::class.java)

        listener.invocationCounter.shouldBe(1)
    }
})
