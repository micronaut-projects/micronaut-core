package io.micronaut.inject.scope

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class PrototypeScopeSpec extends Specification {

    void "test prototype scope"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()

        expect:
        ctx.getBean(BeanPrototype) != ctx.getBean(BeanPrototype)
    }
}
