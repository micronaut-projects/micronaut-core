package io.micronaut.docs.server.routing

import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Specification

import javax.inject.Inject

@Property(name = "spec.name", value = "BackwardCompatibleControllerSpec")
@MicronautTest
class BackwardCompatibleControllerSpec extends Specification {

    @Inject
    @Client("/")
    HttpClient client

    void "test hello world response"() {
        when:
        String response = client.toBlocking()
                .retrieve(HttpRequest.GET("/hello/World"))

        then:
        response == "Hello, World"

        when:
        response = client.toBlocking()
                .retrieve(HttpRequest.GET("/hello/person/John"))

        then:
        response == "Hello, John"
    }
}
