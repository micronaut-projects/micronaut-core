package io.micronaut.http.server.util.localeresolution

import io.micronaut.context.ApplicationContext
import io.micronaut.http.server.util.localeresolution.HttpLocaleResolutionConfiguration
import spock.lang.Specification

class HttpLocaleResolutionConfigurationSpec extends Specification {

    void "bean of type HttpLocaleResolutionConfiguration exists"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        when:
        HttpLocaleResolutionConfiguration conf = context.getBean(HttpLocaleResolutionConfiguration)

        then:
        !conf.cookieName.isPresent()

        and:
        !conf.fixed.isPresent()

        and:
        conf.defaultLocale == Locale.getDefault()

        and:
        !conf.sessionAttribute.isPresent()

        and:
        conf.header

        cleanup:
        context.close()
    }
}
