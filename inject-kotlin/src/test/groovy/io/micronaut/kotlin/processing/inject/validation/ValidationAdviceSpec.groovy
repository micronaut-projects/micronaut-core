package io.micronaut.kotlin.processing.inject.validation

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.annotation.processing.test.KotlinCompiler
import io.micronaut.inject.BeanDefinition

class ValidationAdviceSpec extends AbstractKotlinCompilerSpec {

    private static final String ANN_VALIDATED = 'io.micronaut.validation.Validated'

    void "test only the constrained method of a bean is validated"() {
        when:
        BeanDefinition beanDefinition = KotlinCompiler.buildInterceptedBeanDefinition('test.MyBean', '''
package test

import jakarta.inject.Singleton
import jakarta.validation.constraints.NotBlank

@Singleton
open class MyBean {

    open fun constrained(@NotBlank name: String): String = name

    open fun notConstrained(name: String): String = name
}
''')

        then:
        beanDefinition.getRequiredMethod("constrained", String).hasAnnotation(ANN_VALIDATED)
        beanDefinition.findMethod("notConstrained", String).isEmpty()
    }

    void "test only the constrained method of an introduction interface is validated"() {
        when:
        BeanDefinition beanDefinition = KotlinCompiler.buildIntroducedBeanDefinition('test.MyBean', '''
package test

import io.micronaut.kotlin.processing.aop.introduction.Stub
import jakarta.inject.Singleton
import jakarta.validation.constraints.NotBlank

@Stub
@Singleton
interface MyBean {

    fun constrained(@NotBlank name: String): String

    fun notConstrained(name: String): String
}
''')

        then:
        beanDefinition.getRequiredMethod("constrained", String).hasAnnotation(ANN_VALIDATED)
        !beanDefinition.getRequiredMethod("notConstrained", String).hasAnnotation(ANN_VALIDATED)
    }

    void "test only the constrained method of an introduction abstract class is validated"() {
        when:
        BeanDefinition beanDefinition = KotlinCompiler.buildIntroducedBeanDefinition('test.MyBean', '''
package test

import io.micronaut.kotlin.processing.aop.introduction.Stub
import jakarta.inject.Singleton
import jakarta.validation.constraints.NotBlank

@Stub
@Singleton
abstract class MyBean {

    abstract fun constrained(@NotBlank name: String): String

    abstract fun notConstrained(name: String): String
}
''')

        then:
        beanDefinition.getRequiredMethod("constrained", String).hasAnnotation(ANN_VALIDATED)
        !beanDefinition.getRequiredMethod("notConstrained", String).hasAnnotation(ANN_VALIDATED)
    }

    void "test a constrained method inherited from a super interface is validated"() {
        when:
        BeanDefinition beanDefinition = KotlinCompiler.buildIntroducedBeanDefinition('test.MyBean', '''
package test

import io.micronaut.kotlin.processing.aop.introduction.Stub
import jakarta.inject.Singleton
import jakarta.validation.constraints.NotBlank

interface Parent {

    fun constrained(@NotBlank name: String): String

    fun notConstrained(name: String): String
}

@Stub
@Singleton
interface MyBean : Parent
''')

        then:
        beanDefinition.getRequiredMethod("constrained", String).hasAnnotation(ANN_VALIDATED)
        !beanDefinition.getRequiredMethod("notConstrained", String).hasAnnotation(ANN_VALIDATED)
    }

    void "test a method overriding a constrained method is validated"() {
        when:
        BeanDefinition beanDefinition = KotlinCompiler.buildIntroducedBeanDefinition('test.MyBean', '''
package test

import io.micronaut.kotlin.processing.aop.introduction.Stub
import jakarta.inject.Singleton
import jakarta.validation.constraints.NotBlank

interface Parent {

    fun constrained(@NotBlank name: String): String

    fun notConstrained(name: String): String
}

@Stub
@Singleton
interface MyBean : Parent {

    override fun constrained(name: String): String

    override fun notConstrained(name: String): String
}
''')

        then:
        beanDefinition.getRequiredMethod("constrained", String).hasAnnotation(ANN_VALIDATED)
        !beanDefinition.getRequiredMethod("notConstrained", String).hasAnnotation(ANN_VALIDATED)
    }
}
