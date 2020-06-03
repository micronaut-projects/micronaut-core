package io.micronaut.aop.compile

import io.micronaut.AbstractBeanDefinitionSpec
import io.micronaut.inject.BeanDefinition

class ValidatedNonBeanSpec extends AbstractBeanDefinitionSpec {

    void "test a class with only a validation annotation is not a bean"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition("test.DefaultContract", """
package test

import javax.validation.constraints.NotNull
import io.micronaut.context.annotation.*
import javax.inject.Singleton

class DefaultContract implements Contract {

    Long parseLong(@NotNull CharSequence sequence) {
        0L
    }
}

interface Contract {
    Long parseLong(@NotNull CharSequence sequence)
}

""")
        then:
        beanDefinition == null
    }
}
