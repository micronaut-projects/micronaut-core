package io.micronaut.http.server.hostresolver

import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class HttpHeaderHostResolverConfigurationSpec extends Specification {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run()

    void "HttpHeaderHostResolverConfiguration bean exists"() {
        expect:
        applicationContext.containsBean(HttpHeaderHostResolverConfiguration)
    }

    void "The default HTTP Header name used to resolve Host is Host"() {
        expect:
        applicationContext.getBean(HttpHeaderHostResolverConfiguration).getHostHeaderName() == 'Host'
    }

    void "The default HTTP Header name used to resolve protocol is X-Forwarded-For"() {
        expect:
        applicationContext.getBean(HttpHeaderHostResolverConfiguration).getProtocolHeaderName() == 'X-Forwarded-For'
    }
}
