package io.micronaut.http.client

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
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

    void "test response status with custom code and generic reason"() {
        given:
        HttpClient client = HttpClient.create(embeddedServer.getURL())

        when:
        HttpResponse response = client.toBlocking()
                .exchange(HttpRequest.GET("/status-in-HttpResponse-no-reason"))

        then:
        HttpClientResponseException ex = thrown(HttpClientResponseException)
        ex.getStatus().getCode() == 480
        ex.getStatus().getReason() == 'Client Error (480)'

        when:
        response = client.toBlocking()
                .exchange(HttpRequest.GET("/status-in-MutableHttpResponse-no-reason"))

        then:
        ex = thrown(HttpClientResponseException)
        ex.getStatus().getCode() == 480
        ex.getStatus().getReason() == "Client Error (480)"

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
        @Get("/status-in-HttpResponse-no-reason")
        HttpResponse customStatusHttpResponseNoReason() {
            return HttpResponse.status(480)
        }

        @Get("/status-in-HttpResponse-with-reason")
        HttpResponse customStatusHttpResponseWithReason() {
            return HttpResponse.status(380, "My Custom Reason")
        }

        @Get("/status-in-MutableHttpResponse-no-reason")
        HttpResponse customStatusMutableHttpResponseNoReason() {
            return HttpResponse.ok().status(480)
        }

        @Get("/status-in-MutableHttpResponse-with-reason")
        HttpResponse customStatusMutableHttpResponseWithReason() {
            return HttpResponse.ok().status(380, "My Custom Reason")
        }
    }
}
