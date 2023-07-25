package io.micronaut.http.netty

import spock.lang.Specification

class NettyHttpHeadersSpec extends Specification {
    def validation(String key, String value) {
        given:
        def headers = new NettyHttpHeaders()

        when:
        headers.add(key, value)
        then:
        thrown IllegalArgumentException

        when:
        headers.set(key, value)
        then:
        thrown IllegalArgumentException

        where:
        key       | value
        "foo bar" | "baz"
        "fooä"    | "baz"
        null    | "baz"
        ""    | "baz"
        "foo"     | "bar\nbaz"
    }
}
