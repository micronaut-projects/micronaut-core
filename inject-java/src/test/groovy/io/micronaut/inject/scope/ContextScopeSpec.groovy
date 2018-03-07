package io.micronaut.inject.scope

import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.DefaultBeanContext
import spock.lang.Specification

class ContextScopeSpec extends Specification {

    void "test context scope"() {
        given:
        DefaultBeanContext beanContext = new DefaultBeanContext()

        when:"The context is started"
        beanContext.start()

        then:"So is the bean"
        beanContext.@singletonObjects.values().find() { it.bean instanceof A }
    }
}
