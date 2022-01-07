package io.micronaut.http.server.netty

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
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

// Note that NullableBodyController.test2 and NullableBodyController.test5 apply an
// annotation to HttpRequest's generic type argument. However, this is defective under
// Java 8 but was fixed in Java 9.
//
// See: https://bugs.openjdk.java.net/browse/JDK-8031744
//
@Issue('https://github.com/micronaut-projects/micronaut-core/issues/3064')
@MicronautTest
@Property(name = 'spec.name', value = 'NullableBodySpec')
@Requires(sdk = Requires.Sdk.JAVA, version = '9')
class NullableBodySpec2 extends Specification {

    @Inject
    @Client('/')
    HttpClient client

    @Unroll
    def 'test binding of optional body with annotated generic type arg'() {
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
        '/test2'   | null        | 'false'
        '/test2'   | 'test'      | 'true'
        '/test5'   | null        | 'false'
        '/test5'   | 'test'      | 'true'
    }
}
