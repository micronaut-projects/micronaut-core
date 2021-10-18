package io.micronaut.docs.reactor

import io.micronaut.NameRequestBody
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.*
import io.micronaut.http.client.HttpClient
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.ReactorContext
import kotlinx.coroutines.reactor.asCoroutineContext
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.util.context.Context
import reactor.util.function.Tuple2
import reactor.util.function.Tuples
import java.util.*

class ReactorContextPropagationSpec {

    @Test
    fun testKotlinPropagation() {
        val embeddedServer = ApplicationContext.run(EmbeddedServer::class.java,
                mapOf("mdc.reactortestpropagation.enabled" to "true" as Any)
        )
        val client = embeddedServer.applicationContext.getBean(HttpClient::class.java)

        val result: MutableList<Tuple2<String, String>> = Flux.range(1, 1000)
                .flatMap {
                    val tracingId = UUID.randomUUID().toString()
                    val get = HttpRequest.POST<Any>("http://localhost:${embeddedServer.port}/trigger", NameRequestBody("sss-" + tracingId)).header("X-TrackingId", tracingId)
                    Mono.from(client.retrieve(get, String::class.java))
                            .map { Tuples.of(it as String, tracingId) }
                }
                .collectList()
                .block()

        for (t in result) {
            assert(t.t1 == t.t2)
        }

        embeddedServer.stop()
    }


}

@Requires(property = "mdc.reactortestpropagation.enabled")
@Controller
class TestController(private val someService: SomeService) {

    @Post("/trigger")
    suspend fun trigger(request: HttpRequest<*>, @Body requestBody: SomeBody): String {
        return withContext(Dispatchers.IO) {
            someService.findValue()
        }
    }

    // tag::readctx[]
    @Get("/data")
    suspend fun getTracingId(request: HttpRequest<*>): String {
        val reactorContextView = currentCoroutineContext()[ReactorContext.Key]!!.context
        return reactorContextView.get("reactorTrackingId") as String
    }
    // end::readctx[]

}

@Introspected
class SomeBody(val name: String)

@Requires(property = "mdc.reactortestpropagation.enabled")
@Singleton
class SomeService {

    suspend fun findValue(): String {
        delay(50)
        return withContext(Dispatchers.Default) {
            delay(50)
            val context = currentCoroutineContext()[ReactorContext.Key]!!.context
            val reactorTrackingId = context.get("reactorTrackingId") as String
            val suspendTrackingId = context.get("suspendTrackingId") as String
            if (reactorTrackingId != suspendTrackingId) {
                throw IllegalArgumentException()
            }
            suspendTrackingId
        }
    }

}

@Requires(property = "mdc.reactortestpropagation.enabled")
// tag::simplefilter[]
@Filter(Filter.MATCH_ALL_PATTERN)
class ReactorHttpServerFilter : HttpServerFilter {

    override fun doFilter(request: HttpRequest<*>, chain: ServerFilterChain): Publisher<MutableHttpResponse<*>> {
        val trackingId = request.headers["X-TrackingId"] as String
        return Mono.from(chain.proceed(request)).contextWrite {
            it.put("reactorTrackingId", trackingId)
        }
    }

}

// end::simplefilter[]

@Requires(property = "mdc.reactortestpropagation.enabled")
// tag::suspendfilter[]
@Filter(Filter.MATCH_ALL_PATTERN)
class SuspendHttpServerFilter : CoroutineHttpServerFilter {

    override suspend fun filter(request: HttpRequest<*>, chain: ServerFilterChain): MutableHttpResponse<*> {
        val trackingId = request.headers["X-TrackingId"] as String
        return withContext(Context.of("suspendTrackingId", trackingId).asCoroutineContext()) {
            chain.next(request)
        }
    }

}

interface CoroutineHttpServerFilter : HttpServerFilter {

    suspend fun filter(request: HttpRequest<*>, chain: ServerFilterChain): MutableHttpResponse<*>

    override fun doFilter(request: HttpRequest<*>, chain: ServerFilterChain): Publisher<MutableHttpResponse<*>> {
        return mono {
            filter(request, chain)
        }
    }

}

suspend fun ServerFilterChain.next(request: HttpRequest<*>): MutableHttpResponse<*> {
    return this.proceed(request).asFlow().single()
}
// end::suspendfilter[]

