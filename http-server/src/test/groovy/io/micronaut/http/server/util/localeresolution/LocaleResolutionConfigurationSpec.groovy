package io.micronaut.http.server.util.localeresolution

import io.micronaut.context.ApplicationContext
import io.micronaut.core.util.localeresolution.LocaleResolutionConfiguration
import spock.lang.Specification

class LocaleResolutionConfigurationSpec extends Specification {

    void "bean of type HttpLocaleResolutionConfiguration exists"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        when:
        LocaleResolutionConfiguration conf = context.getBean(LocaleResolutionConfiguration)

        then:
        !conf.fixed.isPresent()

        and:
        conf.defaultLocale == Locale.getDefault()

        cleanup:
        context.close()
    }
}
