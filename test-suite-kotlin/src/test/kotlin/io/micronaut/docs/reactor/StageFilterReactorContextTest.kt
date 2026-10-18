package io.micronaut.docs.reactor

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.order.Ordered
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.http.client.HttpClient
import io.micronaut.http.filter.FilterContinuation
import io.micronaut.runtime.server.EmbeddedServer
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.reactor.ReactorContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import java.util.concurrent.CompletionStage

/**
 * The Reactor context written by a reactive filter reaches a suspended route through a
 * downstream filter with a stage continuation.
 */
class StageFilterReactorContextTest {

    @Test
    fun suspendedRouteSeesReactorContextThroughStageFilter() {
        ApplicationContext.run(EmbeddedServer::class.java, mapOf("spec.name" to "StageFilterReactorContextTest")).use { server ->
            server.applicationContext.createBean(HttpClient::class.java, server.url).use { client ->
                assertEquals("acme", client.toBlocking().retrieve("/stage-reactor-context"))
            }
        }
    }
}

@Requires(property = "spec.name", value = "StageFilterReactorContextTest")
@Controller("/stage-reactor-context")
class StageReactorContextController {

    @Get(produces = ["text/plain"])
    suspend fun tenant(): String {
        val context = currentCoroutineContext()[ReactorContext.Key]?.context
        return context?.getOrDefault("tenant", "MISSING") ?: "MISSING"
    }
}

@Requires(property = "spec.name", value = "StageFilterReactorContextTest")
@ServerFilter("/stage-reactor-context")
class StageReactorContextTenantFilter : Ordered {

    @RequestFilter
    fun tenant(continuation: FilterContinuation<Publisher<MutableHttpResponse<*>>>): Publisher<MutableHttpResponse<*>> =
        Mono.from(continuation.proceed()).contextWrite { it.put("tenant", "acme") }

    override fun getOrder(): Int = 0
}

@Requires(property = "spec.name", value = "StageFilterReactorContextTest")
@ServerFilter("/stage-reactor-context")
class StageReactorContextStageFilter : Ordered {

    @RequestFilter
    fun around(continuation: FilterContinuation<CompletionStage<HttpResponse<*>>>): CompletionStage<HttpResponse<*>> =
        continuation.proceed()

    override fun getOrder(): Int = 100
}
