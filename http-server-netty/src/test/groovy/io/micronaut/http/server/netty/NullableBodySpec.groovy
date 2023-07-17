package io.micronaut.http.server.netty

import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import reactor.core.publisher.Flux
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Unroll

@Issue('https://github.com/micronaut-projects/micronaut-core/issues/3064')
@MicronautTest
@Property(name = 'spec.name', value = 'NullableBodySpec')
class NullableBodySpec extends Specification {

    @Inject
    @Client('/')
    HttpClient client

    @Unroll
    def 'test binding of optional body'() {
        when:
        Flux<HttpResponse<String>> flowable = Flux.from(client.exchange(
                HttpRequest<String>.POST(requestUri, requestBody),
                String
        ))
        HttpResponse<String> response = flowable.blockFirst()
        Optional<String> body = response.getBody()

        then:
        response.status() == HttpStatus.OK
        body.isPresent()
        body.get() == expectedResponseBody

        where:
        requestUri | requestBody | expectedResponseBody
        '/test3'   | null        | 'false'
        '/test3'   | 'test'      | 'true'
        '/test4'   | null        | 'false'
        '/test4'   | 'test'      | 'true'
    }

}
