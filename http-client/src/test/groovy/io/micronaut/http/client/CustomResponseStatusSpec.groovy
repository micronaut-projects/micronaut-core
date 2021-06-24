package io.micronaut.http.client

import io.micronaut.core.annotation.NonNull
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.client.netty.FullNettyClientHttpResponse
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class CustomResponseStatusSpec extends Specification {

    @Inject
    EmbeddedServer embeddedServer

    final def GENERIC_REASONS = [
            1: "Informational",
            2: "Success",
            3: "Redirection",
            4: "Client Error",
            5: "Server Error"
    ]

    private String formatGenericReason(int statusCode) {
        int firstDigit = statusCode / 100 as int
        "${GENERIC_REASONS.get(firstDigit)} (${statusCode})"
    }

    void "test response status with custom code and generic reason"() {
        given:
        int statusCode = 480
        HttpClient client = HttpClient.create(embeddedServer.getURL())

        when:
        HttpResponse response = client.toBlocking()
                .exchange(HttpRequest.GET("/status-in-HttpResponse?code=${statusCode}"))

        then:
        HttpClientResponseException ex = thrown(HttpClientResponseException)
        ex.getStatus().getCode() == statusCode
        ex.getStatus().getReason() == formatGenericReason(statusCode)

        when:
        response = client.toBlocking()
                .exchange(HttpRequest.GET("/status-in-MutableHttpResponse?code=${statusCode}"))

        then:
        ex = thrown(HttpClientResponseException)
        ex.getStatus().getCode() == statusCode
        ex.getStatus().getReason() == formatGenericReason(statusCode)

        cleanup:
        client.close()
    }

    void "test response status with custom code and reason"() {
        given:
        HttpClient client = HttpClient.create(embeddedServer.getURL())

        when:
        HttpResponse response = client.toBlocking()
                .exchange(HttpRequest.GET("/status-in-HttpResponse-with-reason"))

        then:
        response.getStatus().getCode() == 380
        response.getStatus().getReason() == "My Custom Reason"

        when:
        response = client.toBlocking()
                .exchange(HttpRequest.GET("/status-in-MutableHttpResponse-with-reason"))

        then:
        response.getStatus().getCode() == 380
        response.getStatus().getReason() == "My Custom Reason"

        cleanup:
        client.close()
    }

    @Controller
    static class CustomStatusController {
        @Get("/status-in-HttpResponse")
        HttpResponse customStatusHttpResponse(@QueryValue int code,
                                              @QueryValue(defaultValue = "") String reason) {
            return reason.isEmpty() ? HttpResponse.status(code) : HttpResponse.status(code, reason)
        }

        @Get("/status-in-MutableHttpResponse")
        HttpResponse customStatusMutableHttpResponse(@QueryValue int code,
                                                     @QueryValue(defaultValue = "") String reason) {
            return reason.isEmpty() ? HttpResponse.ok().status(code) : HttpResponse.ok().status(code, reason)
        }
    }
}
