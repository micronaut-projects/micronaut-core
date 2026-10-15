package io.micronaut.http.server.asyncbody

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.body.AsyncRequestBody
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import kotlinx.coroutines.delay
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import reactor.core.publisher.Flux
import java.util.concurrent.CompletableFuture

@Introspected
data class Item(var name: String = "")

/**
 * A suspend function that answers with a response whose body is a stream keeps its
 * [AsyncRequestBody] until the stream ends, whether it suspended or not: the stream is subscribed
 * to after the function completed, and can be made of the reads of the body.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SuspendStreamedBodyTest {

    private val server = ApplicationContext.run(EmbeddedServer::class.java, mapOf("spec.name" to "SuspendStreamedBodyTest"))
    private val client = server.applicationContext.createBean(HttpClient::class.java, server.url)

    @AfterAll
    fun stopServer() {
        client.stop()
        server.stop()
    }

    @Test
    fun aStreamInAResponseIsMadeOfTheElementsOfTheBody() = echoes("/response")

    @Test
    fun aStreamInAResponseOfAFunctionThatSuspendedIsMadeOfTheElementsOfTheBody() = echoes("/suspended-response")

    private fun echoes(route: String) {
        val items = (0 until 50).joinToString(",", "[", "]") { """{"name":"item-$it"}""" }
        val expected = (0 until 50).joinToString("") { "ITEM-$it" }
        val response = client.toBlocking().retrieve(
            HttpRequest.POST("/suspend-streamed-body$route", items).contentType(MediaType.APPLICATION_JSON_TYPE), String::class.java)
        assertEquals(expected, response.replace(Regex("[^A-Z0-9-]"), ""))
    }

    @Controller("/suspend-streamed-body")
    @Requires(property = "spec.name", value = "SuspendStreamedBodyTest")
    class StreamController {

        @Post(uri = "/response", consumes = [MediaType.APPLICATION_JSON], produces = [MediaType.APPLICATION_JSON_STREAM])
        suspend fun response(body: AsyncRequestBody): HttpResponse<Flux<Item>> = HttpResponse.ok(echo(body))

        @Post(uri = "/suspended-response", consumes = [MediaType.APPLICATION_JSON], produces = [MediaType.APPLICATION_JSON_STREAM])
        suspend fun suspendedResponse(body: AsyncRequestBody): HttpResponse<Flux<Item>> {
            delay(10)
            return HttpResponse.ok(echo(body))
        }

        /**
         * One item for each element of the body, read when the stream of the response is
         * subscribed to, after the function completed.
         */
        private fun echo(body: AsyncRequestBody): Flux<Item> {
            val elements = body.elements(Item::class.java)
            return Flux.create<Item> { sink ->
                elements.forEach { item ->
                    sink.next(Item(item.name.uppercase()))
                    CompletableFuture.completedFuture(null)
                }.whenComplete { _, error ->
                    if (error != null) {
                        sink.error(error)
                    } else {
                        sink.complete()
                    }
                }
            }
        }
    }
}
