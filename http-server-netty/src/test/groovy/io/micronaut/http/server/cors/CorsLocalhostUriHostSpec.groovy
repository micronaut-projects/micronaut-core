package io.micronaut.http.server.cors

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * When a request has forwarding headers and no Host header, the drive-by-localhost guard reads the
 * host of the request URI, which keeps the brackets of an IPv6 literal.
 */
class CorsLocalhostUriHostSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run([
            'micronaut.server.cors.enabled'               : true,
            'micronaut.server.host-resolution.host-header': 'X-Forwarded-Host',
    ])

    void "a loopback URI host #host is local without a Host header"(String host) {
        given:
        CorsFilter filter = context.getBean(CorsFilter)
        def request = HttpRequest.POST("http://${host}:8080/state", 'x')
                .header('X-Forwarded-Host', 'public.example.com')

        expect:
        !request.headers.contains('Host')
        filter.shouldDenyToPreventDriveByLocalhostAttack('http://evil.example', request)

        where:
        host << ['localhost', '127.0.0.1', '[::1]', '[0:0:0:0:0:0:0:1]']
    }
}
