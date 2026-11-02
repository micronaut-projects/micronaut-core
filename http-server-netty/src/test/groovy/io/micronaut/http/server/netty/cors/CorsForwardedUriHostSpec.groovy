package io.micronaut.http.server.netty.cors

import io.micronaut.core.annotation.Nullable
import io.micronaut.http.HttpRequest
import io.micronaut.http.server.HttpServerConfiguration
import io.micronaut.http.server.cors.CorsFilter
import io.micronaut.http.server.util.HttpHostResolver
import spock.lang.Specification

/**
 * With forwarding headers and no Host header, the guard checks the host of the request URI, which
 * {@link URI#getHost()} returns bracketed for an IPv6 literal.
 */
class CorsForwardedUriHostSpec extends Specification {

    void "a forwarded request without a Host header to #uri is treated as local"(String uri) {
        given:
        def config = new HttpServerConfiguration.CorsConfiguration() {
            @Override
            boolean isEnabled() {
                true
            }
        }
        def filter = new TestCorsFilter(config, new HttpHostResolver() {
            @Override
            String resolve(@Nullable HttpRequest request) {
                return "http://public.example.com"
            }
        })
        def request = HttpRequest.POST(uri, 'x').header('X-Forwarded-Host', 'public.example.com')

        expect:
        filter.deny('http://evil.example', request)

        where:
        uri << ['http://[::1]:8080/state', 'http://[0:0:0:0:0:0:0:1]:8080/state', 'http://localhost:8080/state']
    }

    static class TestCorsFilter extends CorsFilter {
        TestCorsFilter(HttpServerConfiguration.CorsConfiguration corsConfiguration, HttpHostResolver resolver) {
            super(corsConfiguration, resolver)
        }

        boolean deny(String origin, HttpRequest<?> request) {
            shouldDenyToPreventDriveByLocalhostAttack(origin, request)
        }
    }
}
