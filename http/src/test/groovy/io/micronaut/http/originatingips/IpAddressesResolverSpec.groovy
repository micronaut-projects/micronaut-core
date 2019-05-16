package io.micronaut.http.originatingips

import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class IpAddressesResolverSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext applicationContext = ApplicationContext.run()

    void "Bean IpAddressesResolver exists"() {
        expect:
        applicationContext.containsBean(IpAddressesResolver)
    }

    void "2 IpAddressesResolver : HeaderIpAddressesResolver and RemoteAddressIpAddressesResolver"() {
        applicationContext.getBeansOfType(IpAddressesResolver).size() == 2
    }
}
