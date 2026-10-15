package io.micronaut.docs.server.functional

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class AuditedRoutesSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
        "spec.name"                                : "AuditedRoutesSpec",
        "micronaut.router.versioning.enabled"      : "true",
        "micronaut.router.versioning.header.enabled": "true"])
    @Shared @AutoCleanup HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    void "the annotations of a route bind a filter"() {
        given:
        BlockingHttpClient http = client.toBlocking()

        when:
        HttpResponse<String> payment = http.exchange(HttpRequest.POST("/payments/10", ""), String)
        HttpResponse<String> refund = http.exchange(HttpRequest.POST("/refunds/5", ""), String)
        HttpResponse<String> prices = http.exchange(HttpRequest.GET("/prices"), String)

        then:
        payment.body() == "paid 10"
        payment.headers.get("X-Audited") == "true"
        refund.body() == "refunded 5"
        refund.headers.get("X-Audited") == "true"
        prices.body() == "prices"
        prices.headers.get("X-Audited") == null
    }

    void "the version annotation of a route selects it"() {
        given:
        BlockingHttpClient http = client.toBlocking()

        when:
        HttpResponse<String> v1 = http.exchange(HttpRequest.GET("/receipts/7").header("X-API-VERSION", "1"), String)
        HttpResponse<String> v2 = http.exchange(HttpRequest.GET("/receipts/7").header("X-API-VERSION", "2"), String)

        then:
        v1.body() == "receipt v1 7"
        v1.headers.get("X-Audited") == null
        v2.body() == "receipt v2 7"
        v2.headers.get("X-Audited") == "true"
    }

    void "the routes of a group have its executor and annotations"() {
        given:
        BlockingHttpClient http = client.toBlocking()

        when:
        HttpResponse<String> users = http.exchange(HttpRequest.GET("/admin/users").header("X-API-VERSION", "2"), String)
        HttpResponse<String> deleted = http.exchange(HttpRequest.DELETE("/admin/users/3").header("X-API-VERSION", "2"), String)

        then: 'the blocking executor, not the event loop'
        !users.body().contains("EventLoop")
        users.headers.get("X-Audited") == "true"
        deleted.body() == "deleted 3"
        deleted.headers.get("X-Audited") == "true"

        when: 'the routes of the group answer the version 2 only'
        http.exchange(HttpRequest.GET("/admin/users").header("X-API-VERSION", "1"), String)

        then:
        HttpClientResponseException v1 = thrown()
        v1.status == HttpStatus.NOT_FOUND
    }

    void "a declared route"() {
        expect:
        client.toBlocking().retrieve(HttpRequest.GET("/balance/main")) == "balance of main"
    }
}
