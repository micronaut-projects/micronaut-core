package io.micronaut.http.originatingips

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class HeaderIpAddressesResolverSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext applicationContext = ApplicationContext.run()

    void "Bean HeaderIpAddressesResolver exists"() {
        expect:
        applicationContext.containsBean(HeaderIpAddressesResolver)
    }

    void "can resolve a list of IPS from X-Forwarded-For header value"() {
        when:
        HeaderIpAddressesResolver resolver = applicationContext.getBean(HeaderIpAddressesResolver)

        then:
        noExceptionThrown()

        when:
        def headers = Stub(HttpHeaders) {
            get(_) >> '34.202.241.227, 10.32.108.32'
        }
        def request = Stub(HttpRequest) {
            getHeaders() >> headers
        }
        List<String> result = resolver.originatingIpAddres(request)

        then:
        result == ['34.202.241.227', '10.32.108.32']
    }
}
