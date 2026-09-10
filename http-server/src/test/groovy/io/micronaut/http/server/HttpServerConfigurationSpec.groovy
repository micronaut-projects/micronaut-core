package io.micronaut.http.server

import io.micronaut.context.ApplicationContext
import io.micronaut.core.util.StringUtils
import io.micronaut.http.server.cors.CorsFilter
import spock.lang.Specification

class HttpServerConfigurationSpec extends Specification {

    void errorResponseIncludeMessageDefaultsToFalse() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run()
        HttpServerConfiguration httpServerConfiguration = applicationContext.getBean(HttpServerConfiguration)

        expect:
        httpServerConfiguration.errorResponseIncludeMessage == HttpServerConfiguration.ErrorResponseIncludeMessageMode.NEVER

        cleanup:
        applicationContext.close()
    }

    void errorResponseIncludeMessageCanBeSetViaConfiguration() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run([
            'micronaut.server.error-response-include-message': 'always'
        ])
        HttpServerConfiguration httpServerConfiguration = applicationContext.getBean(HttpServerConfiguration)

        expect:
        httpServerConfiguration.errorResponseIncludeMessage == HttpServerConfiguration.ErrorResponseIncludeMessageMode.ALWAYS

        cleanup:
        applicationContext.close()
    }

    void errorResponseIncludeMessageCanBeSetToOnParam() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run([
            'micronaut.server.error-response-include-message': 'on-param'
        ])
        HttpServerConfiguration httpServerConfiguration = applicationContext.getBean(HttpServerConfiguration)

        expect:
        httpServerConfiguration.errorResponseIncludeMessage == HttpServerConfiguration.ErrorResponseIncludeMessageMode.ON_PARAM

        cleanup:
        applicationContext.close()
    }

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

    void redispatchNonBlockingOnlyDefaultsToTrue() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run()
        HttpServerConfiguration httpServerConfiguration = applicationContext.getBean(HttpServerConfiguration)

        expect:
        httpServerConfiguration.redispatchNonBlockingOnly

        cleanup:
        applicationContext.close()
    }

    void redispatchNonBlockingOnlyCanBeSetViaConfiguration() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run([
            'micronaut.server.redispatch-non-blocking-only': StringUtils.FALSE
        ])
        HttpServerConfiguration httpServerConfiguration = applicationContext.getBean(HttpServerConfiguration)

        expect:
        !httpServerConfiguration.redispatchNonBlockingOnly

        cleanup:
        applicationContext.close()
    }

    void corsFilterDefaultsToEnabled() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run()
        HttpServerConfiguration httpServerConfiguration = applicationContext.getBean(HttpServerConfiguration)

        expect:
        httpServerConfiguration.cors.filter.enabled
        applicationContext.containsBean(CorsFilter)

        cleanup:
        applicationContext.close()
    }

    void corsFilterCanBeDisabledViaConfiguration() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run([
            'micronaut.server.cors.filter.enabled': StringUtils.FALSE
        ])
        HttpServerConfiguration httpServerConfiguration = applicationContext.getBean(HttpServerConfiguration)

        expect:
        !httpServerConfiguration.cors.filter.enabled
        !applicationContext.containsBean(CorsFilter)

        cleanup:
        applicationContext.close()
    }
}
