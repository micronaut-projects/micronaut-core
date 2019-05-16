package io.micronaut.http.server.hostresolver

import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class HostResolverSpec extends Specification {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run()

    void "HostResolver bean exists"() {
        expect:
        applicationContext.containsBean(HostResolver)
    }
}
