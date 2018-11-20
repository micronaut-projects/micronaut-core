package io.micronaut.http.uri

import spock.lang.Specification
import spock.lang.Unroll

class UriBuilderSpec extends Specification {

    @Unroll("baseUrl: #baseUrl params: #params, skip: #skipEmptyParams => #expected")
    void "verifies encoded uri generation"() {
        expect:
        if (skipEmptyParams) {
            assert new UriBuilder()
                    .baseUrl(baseUrl)
                    .queryParameters(params)
                    .skipEmptyQueryParameters()
                    .build() == expected
        } else {
            assert new UriBuilder()
                    .baseUrl(baseUrl)
                    .queryParameters(params)
                    .build() == expected
        }

        and:
        new UriBuilder()
                .build() == ''

        where:
        baseUrl                    | params                       | skipEmptyParams || expected
        'http://example.com:8080/' | [x: 1024, y: 768, empty: ''] | false           || 'http://example.com:8080/?x=1024&y=768&empty='
        'http://example.com:8080/' | [empty: '']                  | true            || 'http://example.com:8080/'
        'http://example.com:8080/' | [x: 1024, y: 768, empty: ''] | true            || 'http://example.com:8080/?x=1024&y=768'
        null                       | null                         | true            || ''
        null                       | null                         | false           || ''
        'http://example.com:8080/' | [:]                          | false           || 'http://example.com:8080/'
        'http://example.com:8080/' | [:]                          | true            || 'http://example.com:8080/'
    }
}
