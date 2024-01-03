package io.micronaut.inject.configuration

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.test.external.ExternalConfiguration

class ExternalConfigurationSpec extends AbstractTypeElementSpec {

    void "test import a bean with primitives"() {
        given:
        ApplicationContext ctx = ApplicationContext.builder().start()
        when:
        def vault = ctx.getBean(ExternalConfiguration)

        then:
        vault

        cleanup:
        ctx.close()
    }

}
