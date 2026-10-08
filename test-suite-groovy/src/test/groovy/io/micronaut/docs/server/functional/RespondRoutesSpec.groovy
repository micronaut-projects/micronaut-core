package io.micronaut.docs.server.functional

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class RespondRoutesSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ["spec.name": "RespondRoutesSpec"])
    @Shared @AutoCleanup HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    void "the routes answer without a handler"() {
        when:
        HttpResponse<String> ping = client.toBlocking().exchange(HttpRequest.GET("/ping"), String)

        then:
        ping.body() == "pong"
        ping.headers.get("Cache-Control") == "max-age=60"
        // the client follows the redirect
        client.toBlocking().retrieve(HttpRequest.GET("/old-ping")) == "pong"
        client.toBlocking().retrieve(HttpRequest.GET("/visits")) == "visit 1"
        client.toBlocking().retrieve(HttpRequest.GET("/visits")) == "visit 2"
        client.toBlocking().retrieve(HttpRequest.GET("/greetings/World")) == "Hello World"

        when:
        client.toBlocking().retrieve(HttpRequest.POST("/legacy/webhook", "{}"))

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.GONE
    }
}
