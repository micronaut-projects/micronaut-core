package io.micronaut.docs.server.suspend

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldStartWith
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.LoomSupport
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import kotlinx.coroutines.reactive.awaitSingle

class SuspendExecuteOnSpec : StringSpec() {

    val embeddedServer = autoClose(
        ApplicationContext.run(
            EmbeddedServer::class.java,
            mapOf("spec.name" to "SuspendExecuteOnSpec")
        )
    )

    val client = autoClose(
        embeddedServer.applicationContext.createBean(HttpClient::class.java, embeddedServer.url)
    )

    init {
        "suspend function with @ExecuteOn should run on the IO thread pool, not the event loop" {
            val threadName = client.retrieve("/suspend-execute-on/thread").awaitSingle()
            threadName shouldStartWith "${TaskExecutors.IO}-executor"
        }
    }

    @Requires(property = "spec.name", value = "SuspendExecuteOnSpec")
    @Controller("/suspend-execute-on")
    @Produces("text/plain")
    class SuspendExecuteOnController {

        @Get("/thread")
        @ExecuteOn(TaskExecutors.IO)
        suspend fun threadName(): String {
            return Thread.currentThread().name
        }
    }
}
