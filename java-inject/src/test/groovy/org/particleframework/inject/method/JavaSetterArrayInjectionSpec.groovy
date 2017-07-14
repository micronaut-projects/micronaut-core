package org.particleframework.inject.method

import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultBeanContext
import spock.lang.Specification

class JavaSetterArrayInjectionSpec extends Specification {
    void "test injection via setter that takes an array"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:
        JavaB b =  context.getBean(JavaB)

        then:
        b.all != null
        b.all.size() == 2
        b.all.contains(context.getBean(JavaAImpl))
    }
}
