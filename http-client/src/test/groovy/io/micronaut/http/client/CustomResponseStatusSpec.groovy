package io.micronaut.http.client

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.uri.UriBuilder
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class CustomResponseStatusSpec extends Specification {

    @Inject
    EmbeddedServer embeddedServer

    void "test response status custom code and generic reason, from HttpResponse"() {
        given:
        HttpClient client = HttpClient.create(embeddedServer.getURL())
        HttpResponse response = null

        URI uri = UriBuilder.of("/status-in-HttpResponse")
                .queryParam("code", requiredStatusCode)
                .build()

        try {
            response = client.toBlocking().exchange(
                    HttpRequest.GET(uri))
        } catch(HttpClientResponseException ex) {
            HttpStatus status = ex.getResponse().getStatus()
            if (status.getCode() < 400) {
                throw new RuntimeException(
                        ex.getClass().getName() + "should not be thrown on status code ${status.getCode()}")
            }
            response = ex.getResponse()
        }

        expect:
        response.getStatus().getCode() == responseStatusCode
        response.getStatus().getReason() == responseReason

        cleanup:
        client.close()

        where:
        requiredStatusCode | responseStatusCode | responseReason
        180                | 180                | 'Informational (180)'
        280                | 280                | 'Success (280)'
        380                | 380                | 'Redirection (380)'
        480                | 480                | 'Client Error (480)'
        580                | 580                | 'Server Error (580)'
        680                | 680                | 'Unknown Status (680)'
    }

    void "test response status custom code and generic reason, from MutableHttpResponse"() {
        given:
        HttpClient client = HttpClient.create(embeddedServer.getURL())
        HttpResponse response = null

        URI uri = UriBuilder.of("/status-in-MutableHttpResponse")
                .queryParam("code", requiredStatusCode)
                .build()

        try {
            response = client.toBlocking().exchange(
                    HttpRequest.GET(uri))
        } catch(HttpClientResponseException ex) {
            HttpStatus status = ex.getResponse().getStatus()
            if (status.getCode() < 400) {
                throw new RuntimeException(
                        ex.getClass().getName() + "should not be thrown on status code ${status.getCode()}")
            }
            response = ex.getResponse()
        }

        expect:
        response.getStatus().getCode() == responseStatusCode
        response.getStatus().getReason() == responseReason

        cleanup:
        client.close()

        where:
        requiredStatusCode | responseStatusCode | responseReason
        180                | 180                | 'Informational (180)'
        280                | 280                | 'Success (280)'
        380                | 380                | 'Redirection (380)'
        480                | 480                | 'Client Error (480)'
        580                | 580                | 'Server Error (580)'
        680                | 680                | 'Unknown Status (680)'
    }

    void "test response status custom code and reason, from HttpResponse"() {
        given:
        int code = 380
        String reason = "My Custom Reason"
        HttpClient client = HttpClient.create(embeddedServer.getURL())

        when:
        URI uri = UriBuilder.of("/status-in-HttpResponse")
                .queryParam("code", code)
                .queryParam("reason", reason)
                .build()
        HttpResponse response = client.toBlocking()
                .exchange(HttpRequest.GET(uri))

        then:
        response.getStatus().getCode() == code
        response.getStatus().getReason() == reason

        cleanup:
        client.close()
    }

    void "test response status custom code and reason, from MutableHttpResponse"() {
        given:
        int code = 380
        String reason = "My Custom Reason"
        HttpClient client = HttpClient.create(embeddedServer.getURL())

        when:
        URI uri = UriBuilder.of("/status-in-MutableHttpResponse")
                .queryParam("code", code)
                .queryParam("reason", reason)
                .build()
        HttpResponse response = client.toBlocking()
                .exchange(HttpRequest.GET(uri))

        then:
        response.getStatus().getCode() == code
        response.getStatus().getReason() == reason

        cleanup:
        client.close()
    }

    @Controller
    static class CustomStatusController {
        @Get("/status-in-HttpResponse")
        HttpResponse customStatusHttpResponse(@QueryValue int code,
                                              @QueryValue(defaultValue = "") String reason) {
            return reason.isEmpty()
                    ? HttpResponse.status(code)
                    : HttpResponse.status(code, reason)
        }

        @Get("/status-in-MutableHttpResponse")
        HttpResponse customStatusMutableHttpResponse(@QueryValue int code,
                                                     @QueryValue(defaultValue = "") String reason) {
            return reason.isEmpty()
                    ? HttpResponse.ok().status(code)
                    : HttpResponse.ok().status(code, reason)
        }
    }
}
