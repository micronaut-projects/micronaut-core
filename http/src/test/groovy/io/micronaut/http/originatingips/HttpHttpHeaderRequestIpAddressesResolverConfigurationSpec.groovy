package io.micronaut.http.originatingips

import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class HttpHttpHeaderRequestIpAddressesResolverConfigurationSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext applicationContext = ApplicationContext.run()

    void "Bean HeaderIpAddressesResolverConfiguration exists"() {
        expect:
        applicationContext.containsBean(HttpHeaderRequestIpAddressesResolverConfiguration)
    }

    void "default HTTP Header name for HeaderIpAddressesResolverConfiguration is X-Forwarded-For"() {
        expect:
        applicationContext.getBean(HttpHeaderRequestIpAddressesResolverConfiguration).getHeaderName() == 'X-Forwarded-For'
    }

    void "default delimiter for IP Address within the HTTP Header value for HeaderIpAddressesResolverConfiguration is ,"() {
        expect:
        applicationContext.getBean(HttpHeaderRequestIpAddressesResolverConfiguration).getDelimiter() == ','
    }
}
