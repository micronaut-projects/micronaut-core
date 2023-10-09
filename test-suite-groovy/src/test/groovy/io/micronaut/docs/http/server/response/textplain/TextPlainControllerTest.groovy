package io.micronaut.docs.http.server.response.textplain

import io.micronaut.context.annotation.Property
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@Property(name = 'spec.name', value = 'TextPlainControllerTest')
@MicronautTest
class TextPlainControllerTest extends Specification {

    @Client('/txt')
    @Inject
    HttpClient httpClient

    void "test text plain responses for non String types for endpoint #endpoint"(String endpoint, String expectedResult) {
        when:
        String result = httpClient.toBlocking().retrieve(endpoint)

        then:
        result == expectedResult

        where:
        endpoint        |   expectedResult
        '/boolean'      |   'true'
        '/boolean/mono' |   'true'
        '/boolean/flux' |   'true'
        '/bigdecimal'   |   BigDecimal.valueOf(Long.MAX_VALUE).toString()
        '/date'         |   new Calendar.Builder().setDate(2023,7,4).build().toString()
        '/person'       |   new Person('Dean Wette', 65).toString()
    }
}
