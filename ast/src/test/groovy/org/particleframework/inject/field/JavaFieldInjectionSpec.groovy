package org.particleframework.inject.field

import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultBeanContext
import spock.lang.Specification

class JavaFieldInjectionSpec extends Specification {

    void "test injection via setter with interface"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:"Alpha bean is obtained that has a setter with @Inject"
        JavaClass jc =  context.getBean(JavaClass)

        then:"The implementation is injected"
        jc.javaInterface != null
    }
}

