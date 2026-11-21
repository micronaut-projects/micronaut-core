package io.micronaut.http.server.util

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class DefaultHttpHostResolverIpv6Spec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext applicationContext = ApplicationContext.run()

    void "a bracketed IPv6 literal in the Host header is kept whole"(String header, String result) {
        HttpHostResolver hostResolver = applicationContext.getBean(HttpHostResolver)
        def request = Stub(HttpRequest) {
            getHeaders() >> new MockHttpHeaders(['Host': [header]])
            getUri() >> new URI("/")
        }

        expect:
        hostResolver.resolve(request) == result

        where:
        header                   | result
        '[::1]:8080'             | 'http://[::1]:8080'
        '[0:0:0:0:0:0:0:1]:8080' | 'http://[0:0:0:0:0:0:0:1]:8080'
        '[::1]'                  | 'http://[::1]'
        'localhost:8080'         | 'http://localhost:8080'
    }
}
