package io.micronaut.http.originatingips

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class RemoteAddressIpAddressesResolverSpec extends Specification {
    @Shared
    @AutoCleanup
    ApplicationContext applicationContext = ApplicationContext.run()

    void "Bean RemoteAddressIpAddressesResolver exists"() {
        expect:
        applicationContext.containsBean(RemoteAddressIpAddressesResolver)
    }

    void "can resolve a list of IPS from remoteAddress"() {
        when:
        RemoteAddressIpAddressesResolver resolver = applicationContext.getBean(RemoteAddressIpAddressesResolver)

        then:
        noExceptionThrown()

        when:
        InetAddress inetAddress = InetAddress.getByName("localhost")
        def request = Stub(HttpRequest) {
            getRemoteAddress() >> new InetSocketAddress(inetAddress, 8080)
        }
        List<String> result = resolver.originatingIpAddres(request)

        then:
        result == ['127.0.0.1']
    }
}
