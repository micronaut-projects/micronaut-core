package io.micronaut.http.originatingips

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class IpAddressesResolverAggregatorSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext applicationContext = ApplicationContext.run()

    void "IpAddressesResolverAggregator aggregates every IpAddressesResolver and returns a list of ips"() {
        given:
        IpAddressesResolverAggregator aggregator = applicationContext.getBean(IpAddressesResolverAggregator)
        def headers = Stub(HttpHeaders) {
            get(_) >> '34.202.241.227, 10.32.108.32'
        }
        def request = Stub(HttpRequest) {
            getHeaders() >> headers
            getRemoteAddress() >> new InetSocketAddress(8080)
        }

        when:
        List<String> result = aggregator.originatingIpAddresses(request)

        then:
        result.sort() == ['34.202.241.227', '10.32.108.32', '0.0.0.0'].sort()
    }
}
