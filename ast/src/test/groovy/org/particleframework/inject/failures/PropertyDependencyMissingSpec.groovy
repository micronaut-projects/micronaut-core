package org.particleframework.inject.failures

import org.particleframework.context.Context
import org.particleframework.context.DefaultContext
import org.particleframework.context.exceptions.DependencyInjectionException
import spock.lang.Specification

import javax.inject.Inject

/**
 * Created by graemerocher on 12/05/2017.
 */
class PropertyDependencyMissingSpec  extends Specification {


    void "test a useful exception is thrown when a dependency injection failure occurs"() {
        given:
        Context context = new DefaultContext()
        context.start()

        when:"A bean is obtained that has a setter with @Inject"
        B b =  context.getBean(B)

        then:"The correct error is thrown"
        def e = thrown(DependencyInjectionException)
        e.message == 'Failed to inject value for parameter [a] of method [setA] of class: org.particleframework.inject.failures.PropertyDependencyMissingSpec$B'
    }

    static interface A {

    }

    static class B {
        @Inject
        A a
    }

}