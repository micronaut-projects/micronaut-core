package io.micronaut.runtime.context.scope

import io.micronaut.inject.BeanDefinition
import io.micronaut.support.AbstractTypeElementSpec

import javax.inject.Scope

class ThreadLocalJavaParseSpec extends AbstractTypeElementSpec {

    void "test parse bean definition data"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.ThreadLocalBean', '''
package test;

import io.micronaut.runtime.context.scope.*;

@io.micronaut.runtime.context.scope.ThreadLocal()
class ThreadLocalBean {

}
''')

        then:
        beanDefinition.getAnnotationNameByStereotype(Scope).get() == ThreadLocal.name

    }
}
