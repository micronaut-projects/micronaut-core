package io.micronaut.http.originatingips

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class HttpHeaderRequestIpAddressesResolverSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext applicationContext = ApplicationContext.run()

    void "Bean HeaderIpAddressesResolver exists"() {
        expect:
        applicationContext.containsBean(HttpHeaderRequestIpAddressesResolver)
    }

    void "can resolve a list of IPS from X-Forwarded-For header value"() {
        when:
        HttpHeaderRequestIpAddressesResolver resolver = applicationContext.getBean(HttpHeaderRequestIpAddressesResolver)

        then:
        noExceptionThrown()

        when:
        def headers = Stub(HttpHeaders) {
            get(_) >> '34.202.241.227, 10.32.108.32'
        }
        def request = Stub(HttpRequest) {
            getHeaders() >> headers
        }
        List<String> result = resolver.requestIpAddresses(request)

        then:
        result == ['34.202.241.227', '10.32.108.32']
    }
}
