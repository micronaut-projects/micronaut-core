package io.micronaut.http.originatingips

import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class HttpHttpHeaderRequestIpAddressesResolverConfigurationDisabledSpec extends Specification {
    @Shared
    @AutoCleanup
    ApplicationContext applicationContext = ApplicationContext.run([
            'micronaut.http.originating-ips.header.enabled': false
    ])

    void "Bean HeaderIpAddressesResolverConfiguration can be disabled"() {
        expect:
        !applicationContext.containsBean(HttpHeaderRequestIpAddressesResolverConfiguration)
    }
}
