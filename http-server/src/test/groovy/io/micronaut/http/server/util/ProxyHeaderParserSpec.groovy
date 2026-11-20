package io.micronaut.http.server.util

import io.micronaut.http.HttpRequest
import spock.lang.Specification
import spock.lang.Unroll

class ProxyHeaderParserSpec extends Specification {

    @Unroll
    void "X-Forwarded-Host #value is parsed like the Forwarded host"(String value, String host, Integer port) {
        when:
        def parser = new ProxyHeaderParser(request(["X-Forwarded-Host": [value]]))

        then:
        parser.host == host
        parser.port == port

        when:
        parser = new ProxyHeaderParser(request(["Forwarded": ["host=\"$value\"".toString()]]))

        then:
        parser.host == host
        parser.port == port

        where:
        value        | host    | port
        'host'       | 'host'  | null
        'host:8080'  | 'host'  | 8080
        'host:'      | null    | null
        'host:abc'   | null    | null
        '[::1]'      | '[::1]' | null
        '[::1]:8080' | '[::1]' | 8080
    }

    void "X-Forwarded-Port is used when X-Forwarded-Host is a bracketed IPv6 host without a port"() {
        when:
        def parser = new ProxyHeaderParser(request([
                "X-Forwarded-Host": ["[::1]"],
                "X-Forwarded-Port": ["9000"]
        ]))

        then:
        parser.host == '[::1]'
        parser.port == 9000
    }

    void "X-Forwarded-Port is ignored when X-Forwarded-Host has a port"() {
        when:
        def parser = new ProxyHeaderParser(request([
                "X-Forwarded-Host": ["[::1]:8080"],
                "X-Forwarded-Port": ["9000"]
        ]))

        then:
        parser.host == '[::1]'
        parser.port == 8080
    }

    void "a malformed X-Forwarded-Port is ignored"() {
        when:
        def parser = new ProxyHeaderParser(request([
                "X-Forwarded-Host": ["host"],
                "X-Forwarded-Port": ["abc"]
        ]))

        then:
        parser.host == 'host'
        parser.port == null
    }

    private HttpRequest request(Map<CharSequence, List<String>> headers) {
        Stub(HttpRequest) {
            getHeaders() >> new MockHttpHeaders(headers)
        }
    }
}
