package io.micronaut.http.server.netty.cors

import io.micronaut.core.annotation.Nullable
import io.micronaut.http.HttpRequest
import io.micronaut.http.server.HttpServerConfiguration
import io.micronaut.http.server.cors.CorsFilter
import io.micronaut.http.server.util.HttpHostResolver
import spock.lang.Requires
import spock.lang.Specification

import java.security.Permission
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The CORS localhost guard must parse an IPv6 literal Host without resolving a client-chosen name, which would block
 * the event loop. A name lookup is checked by {@link SecurityManager#checkConnect(String, int)} with port -1, while
 * parsing a literal is not.
 */
@Requires({ !jvm.isJava18Compatible() }) // System.setSecurityManager is disallowed from Java 18
class CorsLocalhostNoNameLookupSpec extends Specification {

    void "the guard does not resolve the name of Host #host"(String host) {
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
        def request = HttpRequest.POST('/state', 'x')
                .header('Host', host)
                .header('X-Forwarded-Host', 'public.example.com')
        def lookups = new CopyOnWriteArrayList<String>()
        SecurityManager previous = System.getSecurityManager()
        System.setSecurityManager(new SecurityManager() {
            @Override
            void checkConnect(String h, int port) {
                if (port == -1) {
                    lookups.add(h)
                }
            }

            @Override
            void checkPermission(Permission perm) {
            }

            @Override
            void checkPermission(Permission perm, Object context) {
            }
        })

        when:
        boolean denied
        try {
            denied = filter.deny('http://evil.example', request)
        } finally {
            System.setSecurityManager(previous)
        }

        then:
        !denied
        lookups.findAll { it.contains('nolookup') }.isEmpty()

        where:
        host << ['[a:nolookup.example]:8080', '[::nolookup.example]', '[nolookup:]']
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
