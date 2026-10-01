package io.micronaut.docs.http.server.cors

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.uri.UriBuilder
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class CorsControllerTest {

    @Test
    fun crossOriginWithAllowedOrigin() {
        withClient { client ->
            assertDoesNotThrow {
                client.toBlocking().exchange<Any, Any>(preflight("https://myui.com"))
            }
        }
    }

    @Test
    fun crossOriginWithNotAllowedOrigin() {
        withClient { client ->
            assertThrows(HttpClientResponseException::class.java) {
                client.toBlocking().exchange<Any, Any>(preflight("https://google.com"))
            }
        }
    }

    private fun withClient(consumer: (HttpClient) -> Unit) {
        ApplicationContext.run(EmbeddedServer::class.java, mapOf("spec.name" to "CorsControllerSpec")).use { server ->
            server.applicationContext.createBean(HttpClient::class.java, server.url).use(consumer)
        }
    }

    private fun preflight(originValue: String): MutableHttpRequest<Any> =
        HttpRequest.OPTIONS<Any>(UriBuilder.of("/hello").build())
            .header(HttpHeaders.ACCEPT, MediaType.TEXT_PLAIN)
            .header(HttpHeaders.ORIGIN, originValue)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name)
}
