package io.micronaut.docs.server.functional

import io.micronaut.context.ApplicationContext
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.cookie.Cookie
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class ConditionRoutesSpec extends Specification {

    @Shared int managementPort = SocketUtils.findAvailableTcpPort()
    @Shared @AutoCleanup EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
        "spec.name"      : "ConditionRoutesSpec",
        "management.port": managementPort])
    @Shared @AutoCleanup HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    void "a condition selects the route"() {
        given:
        BlockingHttpClient http = client.toBlocking()

        expect:
        http.retrieve(HttpRequest.GET("/search")) == "search"
        http.retrieve(HttpRequest.GET("/search").header("X-Beta", "on")) == "beta search"
        http.retrieve(HttpRequest.GET("/search?beta=true")) == "beta search"
    }

    void "a constraint on the path variables"() {
        given:
        BlockingHttpClient http = client.toBlocking()

        expect:
        http.retrieve(HttpRequest.GET("/shops/north/stock")) == "stock of north"

        when:
        http.retrieve(HttpRequest.GET("/shops/west/stock"))

        then:
        HttpClientResponseException notFound = thrown()
        notFound.status == HttpStatus.NOT_FOUND
        http.retrieve(HttpRequest.GET("/items/5")) == "item 5"
        http.retrieve(HttpRequest.GET("/items/lamp")) == "item named lamp"
        http.retrieve(HttpRequest.GET("/items/-1")) == "item named -1"
    }

    void "a filter reads the attributes of the route"() {
        given:
        BlockingHttpClient http = client.toBlocking()

        expect:
        http.retrieve(HttpRequest.GET("/reports/daily").header("X-Role", "auditor")) == "daily report"

        when:
        http.retrieve(HttpRequest.GET("/reports/salaries").header("X-Role", "auditor"))

        then:
        HttpClientResponseException forbidden = thrown()
        forbidden.status == HttpStatus.FORBIDDEN
        http.retrieve(HttpRequest.GET("/reports/salaries").header("X-Role", "admin")) == "salaries"
    }

    void "declarative conditions and matchers"() {
        given:
        BlockingHttpClient http = client.toBlocking()

        expect:
        http.retrieve(HttpRequest.GET("/downloads/app.zip").header("X-Channel", "Canary")) == "download app.zip"
        http.retrieve(HttpRequest.GET("/downloads/app.zip").cookie(Cookie.of("channel", "beta"))) == "download app.zip"
        // the address of the peer, not a forwarded one
        http.retrieve(HttpRequest.GET("/downloads/app.zip").header("X-Channel", "beta").header("X-Forwarded-For", "203.0.113.9")) == "download app.zip"

        when:
        http.retrieve(HttpRequest.GET("/downloads/app.zip"))

        then:
        HttpClientResponseException noChannel = thrown()
        noChannel.status == HttpStatus.NOT_FOUND

        when:
        http.retrieve(HttpRequest.GET("/downloads/app.txt").header("X-Channel", "beta"))

        then:
        HttpClientResponseException notAZip = thrown()
        notAZip.status == HttpStatus.NOT_FOUND
    }

    void "a route on another port"() {
        when:
        client.toBlocking().retrieve(HttpRequest.GET("/management/health"))

        then:
        HttpClientResponseException notFound = thrown()
        notFound.status == HttpStatus.NOT_FOUND

        when:
        HttpClient management = HttpClient.create(URI.create("http://" + server.host + ":" + managementPort).toURL())

        then:
        management.toBlocking().retrieve(HttpRequest.GET("/management/health")) == "UP"

        cleanup:
        management?.close()
    }
}
