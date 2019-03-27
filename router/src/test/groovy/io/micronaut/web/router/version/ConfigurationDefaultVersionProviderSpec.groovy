package io.micronaut.web.router.version

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class ConfigurationDefaultVersionProviderSpec extends Specification {

    void "By Default ConfigurationDefaultVersionProvider bean is not loaded"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run([
                "micronaut.router.versioning.enabled": true
        ])

        expect:
        !applicationContext.containsBean(ConfigurationDefaultVersionProvider)

        cleanup:
        applicationContext.close()
    }

    void "ConfigurationDefaultVersionProvider bean is loaded if micronaut.router.versioning.default-version"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run([
                "micronaut.router.versioning.enabled": true,
                "micronaut.router.versioning.default-version": "1.0",
        ])

        expect:
        applicationContext.containsBean(ConfigurationDefaultVersionProvider)

        cleanup:
        applicationContext.close()
    }
}
