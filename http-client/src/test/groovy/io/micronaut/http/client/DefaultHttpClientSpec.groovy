package io.micronaut.http.client

import io.micronaut.http.client.loadbalance.FixedLoadBalancer
import spock.lang.Specification
import spock.lang.Unroll

class DefaultHttpClientSpec extends Specification {

    @Unroll
    def "Host header for #url is #expected"() {
        given:
        def balancer = new FixedLoadBalancer(new URL(url))
        def configuration = new DefaultHttpClientConfiguration()

        when:
        def header = new DefaultHttpClient(balancer, configuration).getHostHeader(new URI(url))

        then:
        header == expected

        where:
        url                                               | expected
        "https://slave1-6x8-build-agent-2.0.1-5h7sl:8080" | "slave1-6x8-build-agent-2.0.1-5h7sl:8080"
        "https://foo_bar:8080"                            | "foo_bar:8080"
        "https://foobar:8080"                             | "foobar:8080"
        "http://foobar:8080"                              | "foobar:8080"
        "http://foobar"                                   | "foobar"
        "http://foobar:80"                                | "foobar"
        "https://foobar:443"                              | "foobar"
        "https://service.url.com"                         | "service.url.com"
        "https://service.url.com:91"                      | "service.url.com:91"
    }

}
