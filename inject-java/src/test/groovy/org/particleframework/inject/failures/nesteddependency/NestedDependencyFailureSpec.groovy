package org.particleframework.inject.failures.nesteddependency

import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultBeanContext
import org.particleframework.context.exceptions.DependencyInjectionException
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
Failed to inject value for parameter [d] of class: org.particleframework.inject.failures.nesteddependency.C

Message: No bean of type [org.particleframework.inject.failures.nesteddependency.D] exists
Path Taken: B.a --> new A([C c]) --> new C([D d])'''
    }
}
