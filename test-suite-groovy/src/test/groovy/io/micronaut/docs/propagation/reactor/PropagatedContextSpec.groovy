package io.micronaut.docs.propagation.reactor

import io.micronaut.context.annotation.Property
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@Property(name = 'spec.name', value = 'PropagatedContextSpec')
@MicronautTest
class PropagatedContextSpec extends Specification {

    @Inject
    @Client("/")
    HttpClient client

    void "test Mono Request"() {
        when:
        String hello = client.toBlocking().retrieve(HttpRequest.GET('/hello'), Argument.of(String.class))

        then:
        'Hello, World' == hello
    }
}
