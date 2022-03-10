package io.micronaut.kotlin.processing.aop.compile

import io.micronaut.inject.BeanDefinition
import spock.lang.Specification
import static io.micronaut.kotlin.processing.KotlinCompiler.*

class ValidatedNonBeanSpec extends Specification {

    void "test a class with only a validation annotation is not a bean"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition("test.DefaultContract", """
package test

import javax.validation.constraints.NotNull
import io.micronaut.context.annotation.*
import jakarta.inject.Singleton

class DefaultContract: Contract {

    override fun parseLong(@NotNull sequence: CharSequence): Long {
        return 0L
    }
}

interface Contract {
    fun parseLong(@NotNull sequence: CharSequence): Long
}

""")
        then:
        beanDefinition == null
    }
}
