package io.micronaut.docs.server.timeout

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class ReportRoutesSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ["spec.name": "ReportRoutesSpec"])

    @Shared
    @AutoCleanup
    HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    void "the group timeout applies"() {
        expect:
        client.toBlocking().retrieve(HttpRequest.GET("/fn-reports/summary")) == "summary"
    }
}
