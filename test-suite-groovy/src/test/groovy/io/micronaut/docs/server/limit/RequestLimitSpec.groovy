package io.micronaut.docs.server.limit

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.server.limit.RequestLimiter
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class RequestLimitSpec extends Specification {

    @Shared @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
        'spec.name': 'RequestLimitSpec',
        'micronaut.server.limits.reports.max-concurrent': 2
    ])

    @Shared @AutoCleanup
    HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    void "both routes go through the named limiter"() {
        given:
        RequestLimiter limiter = server.applicationContext.getBean(RequestLimiter, Qualifiers.byName("reports"))

        expect:
        client.toBlocking().retrieve(HttpRequest.GET("/limited-reports/1")) == "report 1"
        client.toBlocking().retrieve(HttpRequest.GET("/limited-reports/functional/2")) == "report 2"
        limiter.acquiredCount == 2
    }
}
