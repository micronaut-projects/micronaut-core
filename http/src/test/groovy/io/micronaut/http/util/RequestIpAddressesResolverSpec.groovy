package io.micronaut.http.util

import io.micronaut.context.ApplicationContext
import io.micronaut.http.util.RequestIpAddressesResolver
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class RequestIpAddressesResolverSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext applicationContext = ApplicationContext.run()

    void "Bean IpAddressesResolver exists"() {
        expect:
        applicationContext.containsBean(RequestIpAddressesResolver)

        when:
        RequestIpAddressesResolver resolver = applicationContext.getBean(RequestIpAddressesResolver)

        then:
        resolver instanceof CompositeRequestIpAddressResolver

    }

    void "2 IpAddressesResolver : HeaderIpAddressesResolver and RemoteAddressIpAddressesResolver"() {
        applicationContext.getBeansOfType(RequestIpAddressesResolver).size() == 2
    }
}
