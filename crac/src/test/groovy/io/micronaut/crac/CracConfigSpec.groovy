package io.micronaut.crac

import spock.lang.Specification
import io.micronaut.context.ApplicationContext

class CracConfigSpec extends Specification {

    void "CRaC enabled by default with no custom compat lookup class"() {
        given:
        def ctx = ApplicationContext.run()

        when:
        def cfg = ctx.getBean(CracConfiguration)

        then:
        cfg.enabled
        cfg.cracCompatClass == null

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

        cleanup:
        ctx.close()
    }

    void "CRaC custom compat can be configured"() {
        given:
        def ctx = ApplicationContext.run('crac.crac-compat-class': 'java.lang.String')

        when:
        def cfg = ctx.getBean(CracConfiguration)

        then:
        cfg.enabled
        cfg.cracCompatClass == 'java.lang.String'

        cleanup:
        ctx.close()
    }
}
