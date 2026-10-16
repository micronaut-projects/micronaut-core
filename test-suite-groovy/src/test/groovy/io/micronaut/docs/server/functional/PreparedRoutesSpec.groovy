package io.micronaut.docs.server.functional

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class PreparedRoutesSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ["spec.name": "PreparedRoutesSpec"])
    @Shared @AutoCleanup HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    void "a function answers with a prepared response"() {
        when:
        HttpResponse<String> current = client.toBlocking().exchange(HttpRequest.GET("/release"), String)
        HttpResponse<String> notModified = client.toBlocking().exchange(HttpRequest.GET("/release").header(HttpHeaders.IF_NONE_MATCH, '"2026.10"'), String)

        then:
        current.status == HttpStatus.OK
        current.body() == "release 2026.10"
        current.headers.get(HttpHeaders.ETAG) == '"2026.10"'
        notModified.status == HttpStatus.NOT_MODIFIED
        notModified.headers.get(HttpHeaders.ETAG) == '"2026.10"'
    }
}
