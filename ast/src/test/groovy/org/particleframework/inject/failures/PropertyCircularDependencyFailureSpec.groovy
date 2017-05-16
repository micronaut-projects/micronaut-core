package org.particleframework.inject.failures

import org.particleframework.context.Context
import org.particleframework.context.DefaultContext
import org.particleframework.context.exceptions.CircularDependencyException
import org.particleframework.context.exceptions.DependencyInjectionException
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by graemerocher on 16/05/2017.
 */
class PropertyCircularDependencyFailureSpec extends Specification {

    void "test simple property circular dependency failure"() {
        given:
        Context context = new DefaultContext()
        context.start()

        when:"A bean is obtained that has a setter with @Inject"
        B b =  context.getBean(B)

        then:"The implementation is injected"
        def e = thrown(CircularDependencyException)
        e.message == '''\
Failed to inject value for parameter [a] of method [setA] of class: org.particleframework.inject.failures.PropertyCircularDependencyFailureSpec$A

Message: Circular dependency detected
Path Taken: 
B.setA([A a]) --> A.setB([B b])
^                            |
|                            |
|                            |
+----------------------------+'''
    }

    @Singleton
    static class A {
        @Inject B b
    }

    @Singleton
    static class B {
        @Inject A a
    }
}
