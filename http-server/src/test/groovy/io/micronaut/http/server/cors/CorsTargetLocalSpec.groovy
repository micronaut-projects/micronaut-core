package io.micronaut.http.server.cors

import io.micronaut.http.HttpRequest
import spock.lang.Specification

/**
 * Without a Host header, the target of a forwarded request is the host of its URI, whose IPv6
 * literal keeps its brackets.
 */
class CorsTargetLocalSpec extends Specification {

    void "an absolute IPv6 loopback URI of a forwarded request without a Host is local"() {
        given:
        def request = HttpRequest.POST('http://[::1]:8080/state', 'x')
                .header('X-Forwarded-Host', 'public.example.com')

        expect:
        CorsFilter.isTargetLocal({ 'https://public.example.com' }, request)
    }
}
