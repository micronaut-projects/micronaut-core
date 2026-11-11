package io.micronaut.docs.server.pathvariables

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ItemControllerTest {

    private val server = ApplicationContext.run(EmbeddedServer::class.java, mapOf("spec.name" to "ItemControllerTest"))
    private val client = server.applicationContext.createBean(HttpClient::class.java, server.url)

    @AfterAll
    fun stopServer() {
        client.stop()
        server.stop()
    }

    @Test
    fun readsTheVariablesOfTheRoute() {
        assertEquals("Item 5, page 1", get("/items/5"))
        assertEquals("Item 5, page 3", get("/items/5/3"))
    }

    @Test
    fun readsTheValuesOfAListVariable() {
        assertEquals("Tags [red, green]", get("/tags/red,green"))
        assertEquals("Sum 6", get("/sum/1,2,3"))
    }

    @Test
    fun aValueThatDoesNotConvertIsABadRequest() {
        val e = assertThrows(HttpClientResponseException::class.java) { get("/items/abc") }
        assertEquals(HttpStatus.BAD_REQUEST, e.status)
    }

    @Test
    fun aFilterReadsTheVariablesOfTheRoute() {
        val e = assertThrows(HttpClientResponseException::class.java) { get("/items/7") }
        assertEquals(HttpStatus.GONE, e.status)
    }

    private fun get(uri: String): String = client.toBlocking().retrieve(HttpRequest.GET<Any>(uri))
}
