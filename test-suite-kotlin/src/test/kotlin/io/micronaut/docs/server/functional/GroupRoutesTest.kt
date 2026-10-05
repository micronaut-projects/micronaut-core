package io.micronaut.docs.server.functional

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GroupRoutesTest {

    companion object {
        private lateinit var server: EmbeddedServer
        private lateinit var client: HttpClient

        @JvmStatic
        @BeforeAll
        fun start() {
            server = ApplicationContext.run(EmbeddedServer::class.java, mapOf<String, Any>("spec.name" to "GroupRoutesTest"))
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
    fun theGroupFiltersApplyToEveryRouteOfTheGroup() {
        val http = client.toBlocking()
        val orders = http.exchange(HttpRequest.GET<Any>("/api/orders").header("X-Tenant", "acme"), String::class.java)
        assertEquals("orders of acme", orders.body())
        assertEquals("v1", orders.headers["X-Api"])
        assertEquals("api", orders.headers["X-Served-By"])
        val noTenant = assertThrows<HttpClientResponseException> {
            http.exchange(HttpRequest.GET<Any>("/api/orders"), String::class.java)
        }
        assertEquals(HttpStatus.BAD_REQUEST, noTenant.status)
        assertEquals("v1", noTenant.response.headers["X-Api"])
    }

    @Test
    fun aRouteWithoutAPathIsAtThePrefixOfTheGroup() {
        assertEquals("api of acme", client.toBlocking().retrieve(HttpRequest.GET<Any>("/api").header("X-Tenant", "acme")))
    }

    @Test
    fun aNestedGroupAddsItsFilters() {
        val http = client.toBlocking()
        val forbidden = assertThrows<HttpClientResponseException> {
            http.exchange(HttpRequest.GET<Any>("/api/admin/users").header("X-Tenant", "acme"), String::class.java)
        }
        assertEquals(HttpStatus.FORBIDDEN, forbidden.status)
        assertEquals("users", http.retrieve(HttpRequest.GET<Any>("/api/admin/users").header("X-Tenant", "acme").header("X-Role", "admin")))
    }

    @Test
    fun routeFiltersChangeTheRequestAndReplaceTheResponse() {
        val http = client.toBlocking()
        assertEquals("report 7 as summary", http.retrieve(HttpRequest.GET<Any>("/api/reports/7").header("X-Tenant", "acme")))
        val gone = assertThrows<HttpClientResponseException> {
            http.exchange(HttpRequest.GET<Any>("/api/reports/7").header("X-Tenant", "acme").header("X-Legacy", "true"), String::class.java)
        }
        assertEquals(HttpStatus.GONE, gone.status)
    }

    @Test
    fun serverFiltersFilterEveryRequestOfTheirPatterns() {
        val http = client.toBlocking()
        val notFound = assertThrows<HttpClientResponseException> {
            http.exchange(HttpRequest.GET<Any>("/api/missing").header("X-Tenant", "acme"), String::class.java)
        }
        assertEquals(HttpStatus.NOT_FOUND, notFound.status)
        assertEquals("api", notFound.response.headers["X-Served-By"])
        assertNull(notFound.response.headers["X-Api"])
        assertEquals("orders of acme", http.retrieve(HttpRequest.GET<Any>("/v1/orders").header("X-Tenant", "acme")))
    }

    @Test
    fun theRoutesOfAGroupHaveItsMediaTypesAndItsExecutor() {
        val http = client.toBlocking()
        val saved = http.exchange(HttpRequest.POST("/notes", "hello").contentType(MediaType.TEXT_PLAIN_TYPE), String::class.java)
        assertEquals("saved hello", saved.body())
        assertEquals(MediaType.TEXT_PLAIN, saved.contentType.map { it.name }.orElse(null))
        val unsupported = assertThrows<HttpClientResponseException> {
            http.exchange(HttpRequest.POST("/notes", "{}").contentType(MediaType.APPLICATION_JSON_TYPE), String::class.java)
        }
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, unsupported.status)
        assertEquals("saved pen", http.retrieve(HttpRequest.POST("/notes/items", Item(1, "pen"))
            .contentType(MediaType.APPLICATION_JSON_TYPE)))
        assertEquals("1", http.retrieve(HttpRequest.GET<Any>("/notes/count")))
        val drafts = http.exchange(HttpRequest.GET<Any>("/notes/drafts"), String::class.java)
        assertEquals(MediaType.APPLICATION_JSON, drafts.contentType.map { it.name }.orElse(null))
        assertEquals("[{\"id\":1,\"name\":\"draft\"}]", drafts.body())
        val notAcceptable = assertThrows<HttpClientResponseException> {
            http.exchange(HttpRequest.GET<Any>("/notes/drafts").accept(MediaType.TEXT_PLAIN_TYPE), String::class.java)
        }
        assertEquals(HttpStatus.NOT_ACCEPTABLE, notAcceptable.status)
    }

    @Test
    fun aFilterRunsOnItsExecutorAndTheDeclarationContinuesWithAnd() {
        val audited = client.toBlocking().exchange(HttpRequest.GET<Any>("/audit/1"), String::class.java)
        assertEquals("true", audited.header("X-Audited"))
        val body = audited.body()
        assertNotEquals("audited on none", body)
        assertFalse(body!!.contains("EventLoop"), body)
    }
}
