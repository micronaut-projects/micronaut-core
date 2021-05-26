package io.micronaut.inject.scope

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.BeanDefinition

class StereotypeSpec extends AbstractTypeElementSpec {

    void "test singleton is a declared stereotype"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition("test.Test", """
package test;

import io.micronaut.inject.scope.SingletonAnn;

@SingletonAnn
class Test {

}
""")

        then:
        beanDefinition.getDeclaredAnnotationNameByStereotype(AnnotationUtil.SCOPE).get() == "io.micronaut.inject.scope.SingletonAnn"
    }
}
