package io.micronaut.inject.failures.postconstruct

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.exceptions.BeanInstantiationException
import spock.lang.Specification

class PostConstructExceptionSpec extends Specification {

    void "test error message when a bean has an error in the post construct method"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:"A bean is obtained that has a setter with @Inject"
        B b =  context.getBean(B)

        then:"The implementation is injected"
        def e = thrown(BeanInstantiationException)
        e.message == 'Error instantiating bean of type [io.micronaut.inject.failures.postconstruct.B]: bad'
    }
}
