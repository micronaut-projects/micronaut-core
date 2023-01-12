package io.micronaut.http.server.netty.cors

import io.micronaut.context.ApplicationContext
import io.micronaut.http.server.cors.CorsOriginConverter
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class CorsOriginConverterEnabledSpec extends Specification {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run()

    void "CorsOriginConverter is enabled by default"() {
        expect:
        applicationContext.containsBean(CorsOriginConverter)
    }
}
