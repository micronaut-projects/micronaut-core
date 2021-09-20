package io.micronaut

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.order.Ordered
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.*
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.filter.ClientFilterChain
import io.micronaut.http.filter.HttpClientFilter
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.http.uri.UriBuilder
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.toFlux
import java.net.URI
import java.util.*

class MdcPropagationSpec {

    @Test
    fun testKotlinPropagation() {
        val embeddedServer = ApplicationContext.run(EmbeddedServer::class.java,
                mapOf("mdc.reactortest.enabled" to "true" as Any)
        )
        val client = embeddedServer.applicationContext.getBean(HttpClient::class.java)

        Flux.range(1, 1000)
                .flatMap {
                    val tracingId = UUID.randomUUID().toString()
                    val get = HttpRequest.POST<Any>("http://localhost:${embeddedServer.port}/trigger", NameRequestBody("sss-" + tracingId)).header("X-TrackingId", tracingId)
                    client.retrieve(get, String::class.java)
                }
                .collectList()
                .block()

        embeddedServer.stop()
    }

}

@Requires(property = "mdc.reactortest.enabled")
@Controller
class GreetController {

    @Get("/greet")
    fun greet(@QueryValue("name") name: String) : String = "Hello $name!"
}

@Requires(property = "mdc.reactortest.enabled")
@Controller
class NamingController(private val namingService: NamingService ) {

    @Post("/trigger")
    suspend fun trigger(request: HttpRequest<*>, @Body requestBody: NameRequestBody) : HttpResponse<String> {
        val trackingId = request.headers["X-TrackingId"] as String
        checkTracing(trackingId)
        return withContext(Dispatchers.IO + MDCContext()) {
            checkTracing(trackingId)
            namingService.withName(requestBody.name, trackingId)
        }
    }

    private fun checkTracing(trackingId: String) {
        val mdcTracingId = MDC.get(TRACKING_ID)
        if (trackingId != mdcTracingId) {
            throw IllegalArgumentException("TrackingIds do not match! Request: $trackingId vs. Context: $mdcTracingId")
        }
    }
}
@Introspected
class NameRequestBody(val name: String)

@Requires(property = "mdc.reactortest.enabled")
@Singleton
class NamingService(private val namingClient: NamingClient) {

    suspend fun withName(name: String, trackingId: String): HttpResponse<String> {
        val mdcTracingId = MDC.get(TRACKING_ID)
        if (trackingId != mdcTracingId) {
            throw IllegalArgumentException("TrackingIds do not match! Request: $trackingId vs. Context: $mdcTracingId")
        }
        return withContext(Dispatchers.IO){
            delay(50) // "forcing" the initial thread (event loop) to suspend
            namingClient.getFor(name, trackingId)
        }
    }
}

@Requires(property = "mdc.reactortest.enabled")
@Singleton
class NamingClient(@Client(id = "/") private val client: HttpClient) {

    suspend fun getFor(name: String, trackingId: String): HttpResponse<String> {
        val mdcTracingId = MDC.get(TRACKING_ID)
        if (trackingId != mdcTracingId) {
            throw IllegalArgumentException("TrackingIds do not match! Request: $trackingId vs. Context: $mdcTracingId")
        }
        return withContext(Dispatchers.IO) {
            val uri: URI = UriBuilder.of("/greet")
                    .queryParam("name", name)
                    .build()

            val request = HttpRequest.GET<String>(uri).apply {
                header("X-TrackingId", trackingId)
            }

            client.exchange(request, String::class.java).asFlow().single()
        }
    }
}


@Requires(property = "mdc.reactortest.enabled")
@Filter("/trigger")
class HttpApplicationEnterFilter : HttpServerFilter {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun doFilter(request: HttpRequest<*>, chain: ServerFilterChain): Publisher<MutableHttpResponse<*>> {
        val trackingId = request.headers["X-TrackingId"]
        MDC.put(TRACKING_ID, trackingId)
        logger.info("Application enter ($trackingId).")

        return Mono.from(chain.proceed(request))
                .doOnNext() {
                    logger.info("Application exit ($trackingId).")
                }
    }
}

@Requires(property = "mdc.reactortest.enabled")
@Filter("/greet")
class HttpClientFilter : HttpClientFilter {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun getOrder(): Int {
        return Ordered.HIGHEST_PRECEDENCE
    }

    override fun doFilter(request: MutableHttpRequest<*>, chain: ClientFilterChain): Publisher<out HttpResponse<*>> {
        val trackingId: String = request.headers["X-TrackingId"] as String
        val mdcTracingId = MDC.get(TRACKING_ID)
        if (trackingId != mdcTracingId) {
            throw IllegalArgumentException("TrackingIds do not match! Request: $trackingId vs. Context: $mdcTracingId")
        }
        return Mono.from(chain.proceed(request))
                .doOnNext { logRemoteRequestStatus(it, trackingId) }
    }

    private fun logRemoteRequestStatus(response: HttpResponse<*>, trackingId: String) {
        logger.info("Response Status {} was returned ({})", response.status.code, trackingId)
    }
}

const val TRACKING_ID: String = "trackingId"
