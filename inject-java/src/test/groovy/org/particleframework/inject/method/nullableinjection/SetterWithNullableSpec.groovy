package org.particleframework.inject.method.nullableinjection

import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultBeanContext
import org.particleframework.context.exceptions.DependencyInjectionException
import org.particleframework.context.exceptions.NoSuchBeanException
import spock.lang.Specification

class SetterWithNullableSpec extends Specification {

    void "test injection of nullable objects"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:"A bean is obtained that has an setter with @Inject and @Nullable"
        B b =  context.getBean(B)

        then:"The implementation is not injected, but null is"
        b.a == null
    }

    void "test normal injection still fails"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:"A bean is obtained that has an setter with @Inject"
        C c =  context.getBean(C)

        then:"The bean is not found"
        thrown(DependencyInjectionException)
    }
}