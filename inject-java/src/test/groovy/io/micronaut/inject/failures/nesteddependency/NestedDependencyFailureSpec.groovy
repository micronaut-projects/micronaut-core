package io.micronaut.inject.failures.nesteddependency

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.exceptions.DependencyInjectionException
import spock.lang.Specification

class NestedDependencyFailureSpec extends Specification {

    void "test injection via setter with interface"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:"A bean is obtained that has a setter with @Inject"
        B b =  context.getBean(B)

        then:"The implementation is injected"
        def e = thrown(DependencyInjectionException)

        e.message == '''\
Failed to inject value for parameter [d] of class: io.micronaut.inject.failures.nesteddependency.C

Message: No bean of type [io.micronaut.inject.failures.nesteddependency.D] exists. If you are using Java or Kotlin make sure you have enabled annotation processing.
Path Taken: B.a --> new A([C c]) --> new C([D d])'''
    }
}
