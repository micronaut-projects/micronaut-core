package io.micronaut.docs.server.functional

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ItemRoutesTest {

    companion object {
        private lateinit var server: EmbeddedServer
        private lateinit var client: HttpClient

        @JvmStatic
        @BeforeAll
        fun start() {
            server = ApplicationContext.run(EmbeddedServer::class.java, mapOf<String, Any>("spec.name" to "ItemRoutesTest"))
            client = server.applicationContext.createBean(HttpClient::class.java, server.url)
        }

        @JvmStatic
        @AfterAll
        fun stop() {
            client.stop()
            server.stop()
        }
    }

    @Test
    fun itemsAreCreatedReadAndDeleted() {
        val http = client.toBlocking()
        val created = http.exchange(HttpRequest.POST("/items", Item(0, "pen")), Item::class.java)
        assertEquals(HttpStatus.CREATED, created.status)
        val id = created.body()!!.id
        assertEquals(Item(id, "pen"), http.retrieve(HttpRequest.GET<Any>("/items/$id"), Item::class.java))
        assertEquals("pen", http.retrieve(HttpRequest.GET<Any>("/items/$id/name")))
        assertEquals("touched $id with PATCH", http.retrieve(HttpRequest.PATCH("/items/$id/touch", "")))
        assertEquals("touched $id with PUT", http.retrieve(HttpRequest.PUT("/items/$id/touch", "")))
        assertTrue(http.retrieve(HttpRequest.GET<Any>("/items/count"), Int::class.java) >= 1)
        assertEquals(HttpStatus.NO_CONTENT, http.exchange<Any, Any>(HttpRequest.DELETE<Any>("/items/$id")).status)
        val notFound = assertThrows<HttpClientResponseException> {
            http.retrieve(HttpRequest.GET<Any>("/items/$id"))
        }
        assertEquals(HttpStatus.NOT_FOUND, notFound.status)
    }

    @Test
    fun aCustomMethodAndANullableBody() {
        val http = client.toBlocking()
        assertEquals("items: PROPFIND", http.retrieve(HttpRequest.create<Any>(HttpMethod.CUSTOM, "/items", "PROPFIND")))
        assertEquals(HttpStatus.NO_CONTENT, http.exchange<Any, Any>(HttpRequest.create<Any>(HttpMethod.POST, "/items/optional")
            .contentType(MediaType.APPLICATION_JSON_TYPE)).status)
        assertEquals(HttpStatus.CREATED, http.exchange<Item, Any>(HttpRequest.POST("/items/optional", Item(0, "cup"))).status)
    }

    @Test
    fun anImplicitHeadRouteAndAMethodNotAllowed() {
        val http = client.toBlocking()
        val item = http.retrieve(HttpRequest.POST("/items", Item(0, "book")), Item::class.java)
        assertEquals(HttpStatus.OK, http.exchange(HttpRequest.HEAD("/items/" + item.id), Any::class.java).status)
        val notAllowed = assertThrows<HttpClientResponseException> {
            http.exchange<String, Any>(HttpRequest.PATCH("/items/" + item.id, ""))
        }
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, notAllowed.status)
    }
}
