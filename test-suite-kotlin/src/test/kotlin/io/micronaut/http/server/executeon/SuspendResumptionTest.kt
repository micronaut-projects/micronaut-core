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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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

    @Test
    fun `child coroutines of a route with ExecuteOn inherit that executor`() {
        val (first, second) = threadsFor("/nested-executeOn")
        assertOnIoExecutor(first, "the first child coroutine of a route with @ExecuteOn(IO)")
        assertOnIoExecutor(second, "the second child coroutine of a route with @ExecuteOn(IO)")
    }

    @Test
    fun `child coroutines of a route without ExecuteOn are not diverted onto the executor`() {
        val (first, second) = threadsFor("/nested-plain")
        assertNotOnIoExecutor(first, "the first child coroutine of a route with no executor")
        assertNotOnIoExecutor(second, "the second child coroutine of a route with no executor")
    }

    @Test
    fun `an explicit dispatcher wins over the route's executor, which is restored afterwards`() {
        val (inside, after) = threadsFor("/explicit-dispatcher")
        assertNotOnIoExecutor(inside, "a withContext(Dispatchers.Default) block")
        assertOnIoExecutor(after, "a route with @ExecuteOn(IO), after a withContext block returns")
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

        @Get("/nested-executeOn")
        @ExecuteOn(TaskExecutors.IO)
        suspend fun nestedWithExecuteOn(): String = threadsOfChildCoroutines()

        @Get("/nested-plain")
        suspend fun nestedPlain(): String = threadsOfChildCoroutines()

        @Get("/explicit-dispatcher")
        @ExecuteOn(TaskExecutors.IO)
        suspend fun explicitDispatcher(): String {
            val inside = withContext(Dispatchers.Default) { Thread.currentThread().name }
            return "$inside|${Thread.currentThread().name}"
        }

        private suspend fun beforeAndAfterSuspension(): String {
            val before = Thread.currentThread().name
            delay(5.milliseconds)
            return "$before|${Thread.currentThread().name}"
        }

        // children inherit the dispatcher from the route's coroutine context, so @ExecuteOn reaches them too
        private suspend fun threadsOfChildCoroutines(): String = coroutineScope {
            val first = async { Thread.currentThread().name }
            val second = async { Thread.currentThread().name }
            "${first.await()}|${second.await()}"
        }
    }
}
