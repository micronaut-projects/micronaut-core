package io.micronaut.docs.server.functional

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class BenchmarkRoutesSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ["spec.name": "BenchmarkRoutesSpec"])
    @Shared @AutoCleanup HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    void "the plaintext test"() {
        when:
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET("/plaintext"), String)

        then:
        response.body() == "Hello, World!"
        response.headers.get("Content-Type") == "text/plain"
        response.headers.get("Content-Length") == "13"
        response.headers.get("Server") == "Micronaut"
        ZonedDateTime.parse(response.headers.get("Date"), DateTimeFormatter.RFC_1123_DATE_TIME)
        response.headers.get("Content-Encoding") == null
    }

    void "the JSON test"() {
        when:
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET("/json"), String)

        then:
        response.body() == '{"message":"Hello, World!"}'
        response.headers.get("Content-Type") == "application/json"
        response.headers.get("Content-Length") == "27"
        response.headers.get("Server") == "Micronaut"
        ZonedDateTime.parse(response.headers.get("Date"), DateTimeFormatter.RFC_1123_DATE_TIME)
    }
}
