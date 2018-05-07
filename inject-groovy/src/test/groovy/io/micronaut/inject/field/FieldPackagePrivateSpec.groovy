package io.micronaut.inject.field

import groovy.transform.PackageScope
import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Singleton

class FieldPackagePrivateSpec extends Specification {

    void "test that a package private field is injected correctly"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:
        B b = context.getBean(B)

        then:
        b.a instanceof AImpl
    }

    static class B {
        @Inject @PackageScope A a
    }

    static interface A {}

    @Singleton
    static class AImpl implements A {}
}
