package io.micronaut.crac

import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.crac.support.CracResourceRegistrar
import spock.lang.Specification
import io.micronaut.context.ApplicationContext

class CracConfigurationSpec extends Specification {

    void "CRaC enabled by default with no custom compat lookup class"() {
        given:
        def ctx = ApplicationContext.run()

        when:
        def cfg = ctx.getBean(CracConfiguration)

        then:
        cfg.enabled

        when:
        ctx.getBean(CracResourceRegistrar)

        then:
        noExceptionThrown()

        cleanup:
        ctx.close()
    }

    void "CRaC can be disabled"() {
        given:
        def ctx = ApplicationContext.run('crac.enabled': 'false')

        when:
        def cfg = ctx.getBean(CracConfiguration)

        then:
        !cfg.enabled

        when:
        ctx.getBean(CracResourceRegistrar)

        then:
        thrown(NoSuchBeanException)

        cleanup:
        ctx.close()
    }
}
