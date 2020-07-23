package io.micronaut.aop

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class CombinedBeanSpec extends Specification {

    void "test a bean with both AOP and executable methods"() {
        given:
        ApplicationContext ctx = ApplicationContext.run('spec.name': CombinedBeanSpec.simpleName)

        expect:
        ctx.getBean(CombinedBean) != null
    }
}
