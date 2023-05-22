package io.micronaut.http.server.netty.cors

import io.micronaut.context.ApplicationContext
import io.micronaut.http.server.cors.CorsFilter
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class CorsFilterEnabledSpec extends Specification {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run()

    void "CorsFilter is enabled by default"() {
        expect:
        applicationContext.containsBean(CorsFilter)
    }
}
