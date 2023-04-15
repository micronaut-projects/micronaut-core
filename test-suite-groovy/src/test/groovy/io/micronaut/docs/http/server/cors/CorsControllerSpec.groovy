package io.micronaut.docs.http.server.cors

import io.micronaut.context.ApplicationContext
import io.micronaut.core.util.CollectionUtils
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.uri.UriBuilder
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class CorsControllerSpec extends Specification {

    void "CrossOrigin with allowed Origin"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, CollectionUtils.mapOf("spec.name", "CorsControllerSpec"))
        HttpRequest<?> request = preflight(UriBuilder.of("/hello"), "https://myui.com", HttpMethod.GET)
        HttpClient httpClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.URL)
        BlockingHttpClient client = httpClient.toBlocking()

        when:
        client.exchange(request)

        then:
        noExceptionThrown()

        cleanup:
        httpClient.close()
        embeddedServer.close()
    }

    void "CrossOrigin with not allowed Origin"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, CollectionUtils.mapOf("spec.name", "CorsControllerSpec"))
        HttpRequest<?> request = preflight(UriBuilder.of("/hello"), "https://google.com", HttpMethod.GET)
        HttpClient httpClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.URL)
        BlockingHttpClient client = httpClient.toBlocking()

        when:
        client.exchange(request)

        then:
        thrown(HttpClientResponseException)

        cleanup:
        httpClient.close()
        embeddedServer.close()
    }

    private static MutableHttpRequest<?> preflight(UriBuilder uriBuilder, String originValue, HttpMethod method) {
        return HttpRequest.OPTIONS(uriBuilder.build())
            .header(HttpHeaders.ACCEPT, MediaType.TEXT_PLAIN)
            .header(HttpHeaders.ORIGIN, originValue)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method)
    }
}
