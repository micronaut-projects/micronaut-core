package io.micronaut.runtime.context.scope

import io.micronaut.inject.BeanDefinition

import javax.inject.Scope

class ThreadLocalJavaParseSpec extends io.micronaut.annotation.processing.test.AbstractTypeElementSpec {

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
