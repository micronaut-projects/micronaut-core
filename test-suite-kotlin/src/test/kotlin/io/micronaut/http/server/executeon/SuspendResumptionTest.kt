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
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

/**
 * The route's executor must also be the dispatcher the coroutine resumes on, otherwise work after a suspension
 * point runs on `Dispatchers.Default` regardless of [ExecuteOn].
 * See https://github.com/micronaut-projects/micronaut-core/issues/12326.
 */
@MicronautTest
@Property(name = "spec.name", value = "SuspendResumptionTest")
class SuspendResumptionTest {
    @Inject
    lateinit var server: EmbeddedServer

    @Test
    fun `a suspend route with ExecuteOn stays on that executor across a suspension point`() {
        val (before, after) = threadsFor("/executeOn")
        assertOnIoExecutor(before, "a suspend route with @ExecuteOn(IO)")
        assertOnIoExecutor(after, "a suspend route with @ExecuteOn(IO), after resuming")
    }

    @Test
    fun `a route without ExecuteOn is not diverted onto the executor`() {
        val (before, after) = threadsFor("/plain")
        assertOnEventLoop(before, "a suspend route with no executor")
        assertNotOnIoExecutor(after, "a suspend route with no executor, after resuming")
    }

    private fun threadsFor(path: String): Pair<String, String> =
        server.applicationContext.createBean(HttpClient::class.java, server.uri).use {
            val (before, after) = it.toBlocking().retrieve("/suspend-resumption$path").split("|")
            before to after
        }

    @Requires(property = "spec.name", value = "SuspendResumptionTest")
    @Controller("/suspend-resumption")
    class MyController {
        @Get("/executeOn")
        @ExecuteOn(TaskExecutors.IO)
        suspend fun withExecuteOn(): String = beforeAndAfterSuspension()

        @Get("/plain")
        suspend fun plain(): String = beforeAndAfterSuspension()

        private suspend fun beforeAndAfterSuspension(): String {
            val before = Thread.currentThread().name
            delay(5.milliseconds)
            return "$before|${Thread.currentThread().name}"
        }
    }
}
