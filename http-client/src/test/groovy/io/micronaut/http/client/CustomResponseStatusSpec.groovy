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

    void "test response status custom code and generic reason"() {
        given:
        HttpClient client = HttpClient.create(embeddedServer.getURL())
        HttpResponse response = null

        URI uri = UriBuilder.of("/status-in-${targetObject}")
                .queryParam("code", requiredCode)
                .build()

        try {
            response = client.toBlocking().exchange(
                    HttpRequest.GET(uri))
        } catch(HttpClientResponseException ex) {
            HttpStatus status = ex.getResponse().getStatus()
            if (status.getCode() < 400) {
                throw new RuntimeException(
                        "${ex.getClass().getName()} should not be thrown on status code ${status.getCode()}")
            }
            response = ex.getResponse()
        }

        expect:
        response.getStatus().getCode() == responseCode
        response.getStatus().getReason() == responseReason

        cleanup:
        client.close()

        where:
        targetObject          | requiredCode | responseCode | responseReason
        'HttpResponse'        | 180          | 180          | 'Informational (180)'
        'HttpResponse'        | 280          | 280          | 'Success (280)'
        'HttpResponse'        | 380          | 380          | 'Redirection (380)'
        'HttpResponse'        | 480          | 480          | 'Client Error (480)'
        'HttpResponse'        | 580          | 580          | 'Server Error (580)'
        'HttpResponse'        | 680          | 680          | 'Unknown Status (680)'
        'MutableHttpResponse' | 180          | 180          | 'Informational (180)'
        'MutableHttpResponse' | 280          | 280          | 'Success (280)'
        'MutableHttpResponse' | 380          | 380          | 'Redirection (380)'
        'MutableHttpResponse' | 480          | 480          | 'Client Error (480)'
        'MutableHttpResponse' | 580          | 580          | 'Server Error (580)'
        'MutableHttpResponse' | 680          | 680          | 'Unknown Status (680)'
    }

    void "test response status custom code and reason"() {
        given:
        HttpClient client = HttpClient.create(embeddedServer.getURL())

        URI uri = UriBuilder.of("/status-in-${targetObject}")
                .queryParam("code", requiredCode)
                .queryParam("reason", requiredReason)
                .build()
        HttpResponse response = client.toBlocking()
                .exchange(HttpRequest.GET(uri))

        expect:
        response.getStatus().getCode() == responseCode
        response.getStatus().getReason() == responseReason

        cleanup:
        client.close()

        where:
        targetObject          | requiredCode | requiredReason | responseCode | responseReason
        'HttpResponse'        | 380          | 'My Reason'    | 380          | 'My Reason'
        'MutableHttpResponse' | 380          | 'My Reason'    | 380          | 'My Reason'
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
