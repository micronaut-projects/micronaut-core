package io.micronaut.http.server

import io.micronaut.context.ApplicationContext
import io.micronaut.core.util.StringUtils
import spock.lang.Specification

class HttpServerConfigurationSpec extends Specification {

    void dispatchOptionsRequestDefaultsToFalse() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run()
        HttpServerConfiguration httpServerConfiguration = applicationContext.getBean(HttpServerConfiguration)

        expect:
        !httpServerConfiguration.dispatchOptionsRequests

        cleanup:
        applicationContext.close()
    }

    void dispatchOptionsRequestCanBeSetViaConfiguration() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run([
                'micronaut.server.dispatch-options-requests': StringUtils.TRUE
        ])
        HttpServerConfiguration httpServerConfiguration = applicationContext.getBean(HttpServerConfiguration)

        expect:
        httpServerConfiguration.dispatchOptionsRequests

        cleanup:
        applicationContext.close()
    }
}
