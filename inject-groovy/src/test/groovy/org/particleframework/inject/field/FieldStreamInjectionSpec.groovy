package org.particleframework.inject.field

import groovy.transform.PackageScope
import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultBeanContext
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Singleton
import java.util.stream.Collectors
import java.util.stream.Stream

/**
 * Created by graemerocher on 26/05/2017.
 */
class FieldStreamInjectionSpec extends Specification {
    void "test injection via field that takes a stream"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:
        B b =  context.getBean(B)

        then:
        b.all != null
        b.all.size() == 2
        b.all.contains(context.getBean(AImpl))
        b.another.count() == 2
        b.another2.count() == 2
    }

    static interface A {

    }

    @Singleton
    static class AImpl implements A {

    }

    @Singleton
    static class AnotherImpl implements A {

    }

    static class B {
        @Inject
        private Stream<A> all

        @Inject
        protected Stream<A> another

        @Inject
        @PackageScope
        protected Stream<A> another2

        private List<A> allList

        List<A> getAll() {
            if(allList == null) {
                allList = this.all.collect(Collectors.toList())
            }
            return allList
        }
    }
}
