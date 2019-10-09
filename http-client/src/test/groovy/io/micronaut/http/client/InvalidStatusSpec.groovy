package io.micronaut.http.client

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class InvalidStatusSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    void "test receiving an invalid status code"() {
        given:
        WireMockServer wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort())
        wireMockServer.start()
        wireMockServer.stubFor(WireMock.get("/status-only")
                .willReturn(WireMock.status(519)))
        RxStreamingHttpClient client = context.createBean(RxStreamingHttpClient, new URL("http://localhost:${wireMockServer.port()}"))

        when:
        client.exchange("/status-only", String).blockingFirst()

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == "Invalid HTTP status code: 519"

        when:
        client.dataStream(HttpRequest.GET("/status-only")).blockingFirst()

        then:
        ex = thrown(IllegalArgumentException)
        ex.message == "Invalid HTTP status code: 519"

        cleanup:
        client.close()
        wireMockServer.stop()
    }
}
