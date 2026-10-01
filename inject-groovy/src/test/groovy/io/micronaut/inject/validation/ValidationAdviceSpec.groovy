package io.micronaut.inject.validation

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.writer.BeanDefinitionVisitor

class ValidationAdviceSpec extends AbstractBeanDefinitionSpec {

    private static final String ANN_VALIDATED = 'io.micronaut.validation.Validated'

    void "test only the constrained method of an introduction interface is validated"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('validationadvice.MyBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package validationadvice

import io.micronaut.aop.introduction.*
import jakarta.inject.Singleton
import jakarta.validation.constraints.NotBlank

@Stub
@Singleton
interface MyBean {

    String constrained(@NotBlank String name)

    String notConstrained(String name)
}
''')

        then:
        beanDefinition.getRequiredMethod("constrained", String).hasAnnotation(ANN_VALIDATED)
        !beanDefinition.getRequiredMethod("notConstrained", String).hasAnnotation(ANN_VALIDATED)
    }

    void "test only the constrained method of an introduction abstract class is validated"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('validationadvice.MyBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package validationadvice

import io.micronaut.aop.introduction.*
import jakarta.inject.Singleton
import jakarta.validation.constraints.NotBlank

@Stub
@Singleton
abstract class MyBean {

    abstract String constrained(@NotBlank String name)

    abstract String notConstrained(String name)
}
''')

        then:
        beanDefinition.getRequiredMethod("constrained", String).hasAnnotation(ANN_VALIDATED)
        !beanDefinition.getRequiredMethod("notConstrained", String).hasAnnotation(ANN_VALIDATED)
    }

    void "test a constrained method inherited from a super interface is validated"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('validationadvice.MyBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package validationadvice

import io.micronaut.aop.introduction.*
import jakarta.inject.Singleton
import jakarta.validation.constraints.NotBlank

interface Parent {

    String constrained(@NotBlank String name)

    String notConstrained(String name)
}

@Stub
@Singleton
interface MyBean extends Parent {
}
''')

        then:
        beanDefinition.getRequiredMethod("constrained", String).hasAnnotation(ANN_VALIDATED)
        !beanDefinition.getRequiredMethod("notConstrained", String).hasAnnotation(ANN_VALIDATED)
    }
}
