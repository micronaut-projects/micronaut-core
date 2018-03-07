package io.micronaut.inject.scope

import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.annotation.Context
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.annotation.Context
import spock.lang.Specification

/**
 * Created by graemerocher on 17/05/2017.
 */
class ContextScopeSpec extends Specification {

    void "test context scope"() {
        given:
        DefaultBeanContext beanContext = new DefaultBeanContext()

        when:"The context is started"
        beanContext.start()

        then:"So is the bean"
        beanContext.@singletonObjects.values().find() { it.bean instanceof A }
    }

    @Context
    static class A {

    }
}
