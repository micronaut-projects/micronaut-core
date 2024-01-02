package io.micronaut.inject.configuration

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.test.external.ExternalConfiguration
import spock.lang.PendingFeature

class ExternalConfigurationSpec extends AbstractTypeElementSpec {

    @PendingFeature(reason = "Fixed in 4.3")
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
