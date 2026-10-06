package io.micronaut.docs.server.functional

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class AsyncDirectRoutesSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ["spec.name": "AsyncDirectRoutesSpec"])
    @Shared @AutoCleanup HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    void "a direct route runs on an executor or completes later"() {
        expect:
        client.toBlocking().retrieve(HttpRequest.GET("/reports/2026-q1")) == "Q1: 1200 orders"
        client.toBlocking().retrieve(HttpRequest.GET("/quotes/MNT")) == "42.00"
    }

    void "a declined request continues to the ordinary routes: #path"() {
        when:
        client.toBlocking().retrieve(HttpRequest.GET(path))

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.NOT_FOUND

        where:
        path << ["/reports/2025-q4", "/quotes/XYZ"]
    }
}
