package io.micronaut.docs.server.timeout

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class ReportControllerSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ["spec.name": "ReportControllerSpec"])

    @Shared
    @AutoCleanup
    HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    void "the routes answer within their timeout"() {
        expect:
        client.toBlocking().retrieve(HttpRequest.GET("/reports/summary")) == "summary"
        client.toBlocking().retrieve(HttpRequest.GET("/reports/export")) == "export"
        client.toBlocking().retrieve(HttpRequest.GET("/reports/archive")) == "archive"

        when:
        client.toBlocking().retrieve(HttpRequest.GET("/reports/rebuild"))

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.SERVICE_UNAVAILABLE
    }

    void "a route stops once its deadline expired"() {
        when:
        client.toBlocking().retrieve(HttpRequest.GET("/index/rebuild"))

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.SERVICE_UNAVAILABLE
    }
}
