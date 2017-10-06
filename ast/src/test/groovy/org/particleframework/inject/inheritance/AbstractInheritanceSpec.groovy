package org.particleframework.inject.inheritance

import groovy.transform.PackageScope
import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultBeanContext
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by graemerocher on 15/05/2017.
 */
class AbstractInheritanceSpec extends Specification {

    void "test values are injected for abstract parent class"() {
        given:
        BeanContext context  = new DefaultBeanContext()
        context.start()

        when:"A bean is retrieved that has abstract inherited values"
        B b = context.getBean(B)

        then:"The values are injected"
        b.a != null
        b.another != null
        b.a.is(b.another)
        b.packagePrivate != null
        b.packagePrivate.is(b.another)
    }

    @Singleton
    static class A {

    }

    static abstract class AbstractB {
        // inject via field
        @Inject protected A a
        private A another

        private A packagePrivate;

        // inject via method
        @Inject void setAnother(A a) {
            this.another = a
        }

        @Inject
        @PackageScope
        void setPackagePrivate(A a) {
            this.packagePrivate = a;
        }


        A getA() {
            return a
        }

        A getAnother() {
            return another
        }

        @PackageScope
        A getPackagePrivate() {
            return packagePrivate;
        }

    }

    @Singleton
    static class B extends AbstractB {

    }
}
