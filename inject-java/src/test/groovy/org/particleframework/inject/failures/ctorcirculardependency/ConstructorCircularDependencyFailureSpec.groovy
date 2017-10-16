package org.particleframework.inject.failures.ctorcirculardependency

import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultBeanContext
import org.particleframework.context.exceptions.CircularDependencyException
import spock.lang.Specification

class ConstructorCircularDependencyFailureSpec extends Specification {

    void "test simple constructor circular dependency failure"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:"A bean is obtained that has a setter with @Inject"
        B b =  context.getBean(B)

        then:"The implementation is injected"
        def e = thrown(CircularDependencyException)
        e.message == '''\
Failed to inject value for field [a] of class: org.particleframework.inject.failures.ctorcirculardependency.C

Message: Circular dependency detected
Path Taken: 
B.a --> new A([C c]) --> new C([B b])
^                                  |
|                                  |
|                                  |
+----------------------------------+'''
    }
}

