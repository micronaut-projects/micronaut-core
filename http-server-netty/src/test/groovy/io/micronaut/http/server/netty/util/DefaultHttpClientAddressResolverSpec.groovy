package io.micronaut.http.server.netty.util

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.server.util.HttpClientAddressResolver
import spock.lang.Specification

class DefaultHttpClientAddressResolverSpec extends Specification {

    void "test the configured header has priority"() {
        ApplicationContext context = ApplicationContext.run([
                'micronaut.server.client-address-header': 'X-Real-Ip'
        ])
        HttpClientAddressResolver addressResolver = context.getBean(HttpClientAddressResolver)
        def request = Stub(HttpRequest) {
            getHeaders() >> new MockHttpHeaders([
                    "X-Real-Ip": ["34.202.241.227"],
                    "Forwarded": ["for=\"overridden\", for=10.32.108.32",
                                  "for=10.32.108.99"],
                    "X-Forwarded-For": ["overridden, 10.32.108.32"]
            ])
            getRemoteAddress() >> new InetSocketAddress("overridden", 80)
        }

        when:
        String address = addressResolver.resolve(request)

        then:
        address == "34.202.241.227"

        cleanup:
        context.close()
    }

    void "test client forwarded header has highest precedence"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        HttpClientAddressResolver addressResolver = context.getBean(HttpClientAddressResolver)
        def request = Stub(HttpRequest) {
            getHeaders() >> new MockHttpHeaders([
                    "Forwarded": ["for=\"34.202.241.227\", for=10.32.108.32",
                    "for=10.32.108.99"],
                    "X-Forwarded-For": ["overridden, 10.32.108.32"]
            ])
            getRemoteAddress() >> new InetSocketAddress("overridden", 80)
        }

        when:
        String address = addressResolver.resolve(request)

        then:
        address == "34.202.241.227"

        cleanup:
        context.close()
    }

    void "test client de facto proxy headers have preference"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        HttpClientAddressResolver addressResolver = context.getBean(HttpClientAddressResolver)
        def request = Stub(HttpRequest) {
            getHeaders() >> new MockHttpHeaders([
                    "X-Forwarded-For": ["34.202.241.227, 10.32.108.32"]
            ])
            getRemoteAddress() >> new InetSocketAddress("overridden", 80)
        }

        when:
        String address = addressResolver.resolve(request)

        then:
        address == "34.202.241.227"

        cleanup:
        context.close()
    }

    void "test client remote address is used last resort"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        HttpClientAddressResolver addressResolver = context.getBean(HttpClientAddressResolver)
        def request = Stub(HttpRequest) {
            getHeaders() >> new MockHttpHeaders([:])
            getRemoteAddress() >> new InetSocketAddress("34.202.241.227", 80)
        }

        when:
        String address = addressResolver.resolve(request)

        then:
        address == "34.202.241.227"

        cleanup:
        context.close()
    }

}
