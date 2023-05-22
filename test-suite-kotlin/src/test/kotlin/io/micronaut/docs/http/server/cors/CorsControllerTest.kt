package io.micronaut.docs.http.server.cors

import io.micronaut.context.ApplicationContext
import io.micronaut.http.*
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.uri.UriBuilder
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class CorsControllerTest {
    @Test
    fun crossOriginWithAllowedOrigin() {
        val embeddedServer = ApplicationContext.run(EmbeddedServer::class.java, mapOf("spec.name" to "CorsControllerSpec"))
        val request: HttpRequest<Any> = preflight(UriBuilder.of("/hello"), "https://myui.com", HttpMethod.GET)
        val httpClient = embeddedServer.applicationContext.createBean(HttpClient::class.java, embeddedServer.url)
        val client = httpClient.toBlocking()
        Assertions.assertDoesNotThrow<HttpResponse<Any>> {
            client.exchange(request)
        }
        httpClient.close()
        embeddedServer.close()
    }

    @Test
    fun crossOriginWithNotAllowedOrigin() {
        val embeddedServer = ApplicationContext.run(EmbeddedServer::class.java, mapOf("spec.name" to "CorsControllerSpec"))
        val request: HttpRequest<Any> = preflight(UriBuilder.of("/hello"), "https://google.com", HttpMethod.GET)
        val httpClient = embeddedServer.applicationContext.createBean(
            HttpClient::class.java, embeddedServer.url
        )
        val client = httpClient.toBlocking()
        Assertions.assertThrows(HttpClientResponseException::class.java) {
            val response: HttpResponse<Any> = client.exchange(request)
        }
        httpClient.close()
        embeddedServer.close()
    }

    fun preflight(uriBuilder: UriBuilder, originValue: String, method: HttpMethod): HttpRequest<Any> {
        return HttpRequest.OPTIONS<Any>(uriBuilder.build())
            .header(HttpHeaders.ACCEPT, MediaType.TEXT_PLAIN)
            .header(HttpHeaders.ORIGIN, originValue)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method)
    }
}
