package io.micronaut.docs.server.functional

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class DirectRoutesSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ["spec.name": "DirectRoutesSpec"])
    @Shared @AutoCleanup HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    void "the server answers before the request is created"() {
        expect:
        client.toBlocking().retrieve(HttpRequest.GET("/probe/live")) == "UP"
        client.toBlocking().retrieve(HttpRequest.GET("/probe/db")) == "db UP"
        // a declined request continues to the ordinary route
        client.toBlocking().retrieve(HttpRequest.GET("/assets/logo.png")) == "cached logo.png"
        client.toBlocking().retrieve(HttpRequest.GET("/assets/readme.txt")) == "rendered readme.txt"
        client.toBlocking().retrieve(HttpRequest.GET("/robots.txt")) == "User-agent: *\nDisallow: /private/\n"
    }

    void "a conditional GET reads the request"() {
        when:
        HttpResponse<String> version = client.toBlocking().exchange(HttpRequest.GET("/version"), String)
        HttpResponse<String> notModified = client.toBlocking().exchange(HttpRequest.GET("/version").header(HttpHeaders.IF_NONE_MATCH, '"1.0.0"'), String)

        then:
        version.body() == "1.0.0"
        version.header(HttpHeaders.ETAG) == '"1.0.0"'
        notModified.status == HttpStatus.NOT_MODIFIED
    }

    void "a blocked client is answered with 403"() {
        when:
        client.toBlocking().retrieve(HttpRequest.POST("/orders", "{}").header("User-Agent", "BadBot/1.0"))

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.FORBIDDEN
    }

    void "a request no direct route matches continues to the ordinary routes"() {
        when:
        client.toBlocking().retrieve(HttpRequest.GET("/probe/queue"))

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.NOT_FOUND
    }
}
