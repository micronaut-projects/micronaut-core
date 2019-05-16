package io.micronaut.http.util

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class RequestCompositeRequestIpAddressResolverSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext applicationContext = ApplicationContext.run()

    void "IpAddressesResolverAggregator aggregates every IpAddressesResolver and returns a list of ips"() {
        given:
        CompositeRequestIpAddressResolver resolver = applicationContext.getBean(CompositeRequestIpAddressResolver)
        def headers = Stub(HttpHeaders) {
            get(_) >> '34.202.241.227, 10.32.108.32'
        }
        def request = Stub(HttpRequest) {
            getHeaders() >> headers
            getRemoteAddress() >> new InetSocketAddress(8080)
        }

        when:
        List<String> result = resolver.requestIpAddresses(request)

        then:
        result.sort() == ['34.202.241.227', '10.32.108.32', '0.0.0.0'].sort()
    }
}
