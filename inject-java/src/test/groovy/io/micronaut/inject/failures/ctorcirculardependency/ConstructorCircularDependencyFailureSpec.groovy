package io.micronaut.inject.failures.ctorcirculardependency

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.exceptions.CircularDependencyException
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
Failed to inject value for field [a] of class: io.micronaut.inject.failures.ctorcirculardependency.B

Message: Circular dependency detected
Path Taken: 
B.a --> new A([C c]) --> new C([B b])
^                                  |
|                                  |
|                                  |
+----------------------------------+'''
    }
}

