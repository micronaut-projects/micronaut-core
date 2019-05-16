package io.micronaut.http.util

import io.micronaut.context.ApplicationContext
import io.micronaut.http.util.RemoteAddressRequestIpAddressesResolver
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class RemoteAddressRequestIpAddressesResolverDisabledSpec extends Specification {
    @Shared
    @AutoCleanup
    ApplicationContext applicationContext = ApplicationContext.run([
            'micronaut.http.originating-ips.remote-address.enabled': false
    ])

    void "Bean RemoteAddressIpAddressesResolver can be disabled"() {
        expect:
        !applicationContext.containsBean(RemoteAddressRequestIpAddressesResolver)
    }
}
