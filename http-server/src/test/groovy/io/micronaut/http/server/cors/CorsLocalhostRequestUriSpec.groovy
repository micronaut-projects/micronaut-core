package io.micronaut.http.server.cors

import io.micronaut.http.HttpRequest
import io.micronaut.http.server.HttpServerConfiguration
import io.micronaut.http.server.util.HttpHostResolver
import spock.lang.Specification

/**
 * Without a Host header, the drive-by-localhost guard checks the host of the request URI, which
 * keeps the brackets of an IPv6 literal.
 */
class CorsLocalhostRequestUriSpec extends Specification {

    void "a request to the loopback URI #uri without Host is denied when forwarding headers name another host"(String uri) {
        given:
        HttpHostResolver resolver = Stub(HttpHostResolver) {
            resolve(_) >> 'http://public.example.com'
        }
        CorsFilter filter = new CorsFilter(new HttpServerConfiguration.CorsConfiguration(), resolver)
        HttpRequest<?> request = HttpRequest.POST(uri, 'x').header('X-Forwarded-Host', 'public.example.com')

        expect:
        !request.headers.contains('Host')
        filter.shouldDenyToPreventDriveByLocalhostAttack('http://evil.example', request)

        where:
        uri << [
                'http://localhost:8080/state',
                'http://[::1]:8080/state',
                'http://[0:0:0:0:0:0:0:1]:8080/state',
        ]
    }
}
