package io.micronaut.http.server.executeon

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Test

/**
 * A `suspend` route annotated with [ExecuteOn] used to ignore the annotation and run on the event loop.
 * See https://github.com/micronaut-projects/micronaut-core/issues/12326.
 */
@MicronautTest
@Property(name = "spec.name", value = "SuspendExecuteOnTest")
class SuspendExecuteOnTest {
    @Inject
    lateinit var server: EmbeddedServer

    @Test
    fun `a suspend route honours ExecuteOn`() {
        assertOnIoExecutor(threadFor("/suspending"), "a suspend route with @ExecuteOn(IO)")
    }

    @Test
    fun `a blocking route honours ExecuteOn`() {
        assertOnIoExecutor(threadFor("/blocking"), "a blocking route with @ExecuteOn(IO)")
    }

    @Test
    fun `a route without ExecuteOn is left on the event loop`() {
        assertOnEventLoop(threadFor("/without-execute-on"), "a suspend route with no executor")
    }

    private fun threadFor(path: String): String =
        server.applicationContext.createBean(HttpClient::class.java, server.uri).use {
            it.toBlocking().retrieve("/suspend-execute-on$path")
        }

    @Requires(property = "spec.name", value = "SuspendExecuteOnTest")
    @Controller("/suspend-execute-on")
    class MyController {
        @Get("/suspending")
        @ExecuteOn(TaskExecutors.IO)
        suspend fun suspending(): String = Thread.currentThread().name

        @Get("/blocking")
        @ExecuteOn(TaskExecutors.IO)
        fun blocking(): String = Thread.currentThread().name

        @Get("/without-execute-on")
        suspend fun withoutExecuteOn(): String = Thread.currentThread().name
    }
}
