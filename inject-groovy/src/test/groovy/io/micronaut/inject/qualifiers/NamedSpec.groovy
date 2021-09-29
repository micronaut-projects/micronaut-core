package io.micronaut.inject.qualifiers

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.BeanDefinition

class NamedSpec extends AbstractBeanDefinitionSpec {

    void "test singleton bean with @Named with a constant"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition("test.Test", """
package test

import jakarta.inject.Named
import jakarta.inject.Singleton

@Named(Test.NAME)
@Singleton
class Test {

    public static final String NAME = "testing123"

}
""")

        then:
        noExceptionThrown()
        beanDefinition != null
        beanDefinition.getAnnotationMetadata().stringValue(AnnotationUtil.NAMED).get() == "testing123"
    }

    void "test factory bean with @Named with a constant"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition("test.Test\$Bean0", """
package test

import io.micronaut.context.annotation.Factory
import jakarta.inject.Named
import jakarta.inject.Singleton

@Factory
class Test {

    public static final String NAME = "testing123"

    @Singleton
    @Named(NAME)
    String bean() {
        "bean"
    }

}
""")

        then:
        noExceptionThrown()
        beanDefinition != null
        beanDefinition.getAnnotationMetadata().stringValue(AnnotationUtil.NAMED).get() == "testing123"
    }
}
