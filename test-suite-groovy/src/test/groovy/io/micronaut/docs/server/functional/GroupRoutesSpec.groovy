package io.micronaut.docs.server.functional

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class GroupRoutesSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ["spec.name": "GroupRoutesSpec"])
    @Shared @AutoCleanup HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    void "the group filters apply to every route of the group"() {
        given:
        BlockingHttpClient http = client.toBlocking()

        when:
        HttpResponse<String> orders = http.exchange(HttpRequest.GET("/api/orders").header("X-Tenant", "acme"), String)

        then:
        orders.body() == "orders of acme"
        orders.headers.get("X-Api") == "v1"
        orders.headers.get("X-Served-By") == "api"

        when:
        http.exchange(HttpRequest.GET("/api/orders"), String)

        then:
        HttpClientResponseException noTenant = thrown()
        noTenant.status == HttpStatus.BAD_REQUEST
        noTenant.response.headers.get("X-Api") == "v1"
    }

    void "a route without a path is at the prefix of the group"() {
        expect:
        client.toBlocking().retrieve(HttpRequest.GET("/api").header("X-Tenant", "acme")) == "api of acme"
    }

    void "a nested group adds its filters"() {
        given:
        BlockingHttpClient http = client.toBlocking()

        when:
        http.exchange(HttpRequest.GET("/api/admin/users").header("X-Tenant", "acme"), String)

        then:
        HttpClientResponseException forbidden = thrown()
        forbidden.status == HttpStatus.FORBIDDEN
        http.retrieve(HttpRequest.GET("/api/admin/users").header("X-Tenant", "acme").header("X-Role", "admin")) == "users"
    }

    void "route filters change the request and replace the response"() {
        given:
        BlockingHttpClient http = client.toBlocking()

        expect:
        http.retrieve(HttpRequest.GET("/api/reports/7").header("X-Tenant", "acme")) == "report 7 as summary"

        when:
        http.exchange(HttpRequest.GET("/api/reports/7").header("X-Tenant", "acme").header("X-Legacy", "true"), String)

        then:
        HttpClientResponseException gone = thrown()
        gone.status == HttpStatus.GONE
    }

    void "server filters filter every request of their patterns"() {
        given:
        BlockingHttpClient http = client.toBlocking()

        when:
        http.exchange(HttpRequest.GET("/api/missing").header("X-Tenant", "acme"), String)

        then:
        HttpClientResponseException notFound = thrown()
        notFound.status == HttpStatus.NOT_FOUND
        notFound.response.headers.get("X-Served-By") == "api"
        notFound.response.headers.get("X-Api") == null
        http.retrieve(HttpRequest.GET("/v1/orders").header("X-Tenant", "acme")) == "orders of acme"
    }

    void "the routes of a group have its media types and its executor"() {
        given:
        BlockingHttpClient http = client.toBlocking()

        when:
        HttpResponse<String> saved = http.exchange(HttpRequest.POST("/notes", "hello").contentType(MediaType.TEXT_PLAIN_TYPE), String)

        then:
        saved.body() == "saved hello"
        saved.contentType.map { it.name }.orElse(null) == MediaType.TEXT_PLAIN

        when:
        http.exchange(HttpRequest.POST("/notes", "{}").contentType(MediaType.APPLICATION_JSON_TYPE), String)

        then:
        HttpClientResponseException unsupported = thrown()
        unsupported.status == HttpStatus.UNSUPPORTED_MEDIA_TYPE
        http.retrieve(HttpRequest.POST("/notes/items", new Item(1, "pen"))
            .contentType(MediaType.APPLICATION_JSON_TYPE)) == "saved pen"
        http.retrieve(HttpRequest.GET("/notes/count")) == "1"

        when:
        HttpResponse<String> drafts = http.exchange(HttpRequest.GET("/notes/drafts"), String)

        then:
        drafts.contentType.map { it.name }.orElse(null) == MediaType.APPLICATION_JSON
        drafts.body() == '[{"id":1,"name":"draft"}]'

        when:
        http.exchange(HttpRequest.GET("/notes/drafts").accept(MediaType.TEXT_PLAIN_TYPE), String)

        then:
        HttpClientResponseException notAcceptable = thrown()
        notAcceptable.status == HttpStatus.NOT_ACCEPTABLE
    }

    void "a filter runs on its executor and the declaration continues with and"() {
        when:
        HttpResponse<String> audited = client.toBlocking().exchange(HttpRequest.GET("/audit/1"), String)

        then:
        audited.header("X-Audited") == "true"
        audited.body() != "audited on none"
        !audited.body().contains("EventLoop")
    }
}
