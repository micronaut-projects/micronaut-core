package io.micronaut.inject.validation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.writer.BeanDefinitionVisitor
import io.micronaut.inject.writer.BeanDefinitionWriter

class ValidationAdviceSpec extends AbstractTypeElementSpec {

    private static final String ANN_VALIDATED = 'io.micronaut.validation.Validated'

    void "test only the constrained method of a bean is validated"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.$MyBean' + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionWriter.PROXY_SUFFIX, '''
package test;

import jakarta.validation.constraints.NotBlank;

@jakarta.inject.Singleton
class MyBean {

    public String constrained(@NotBlank String name) {
        return name;
    }

    public String notConstrained(String name) {
        return name;
    }
}
''')

        then:
        beanDefinition.getRequiredMethod("constrained", String).hasAnnotation(ANN_VALIDATED)
        beanDefinition.findMethod("notConstrained", String).isEmpty()
    }

    void "test only the constrained method of an introduction interface is validated"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test;

import io.micronaut.aop.introduction.*;
import jakarta.validation.constraints.NotBlank;

@Stub
@jakarta.inject.Singleton
interface MyBean {

    String constrained(@NotBlank String name);

    String notConstrained(String name);
}
''')

        then:
        beanDefinition.getRequiredMethod("constrained", String).hasAnnotation(ANN_VALIDATED)
        !beanDefinition.getRequiredMethod("notConstrained", String).hasAnnotation(ANN_VALIDATED)
    }

    void "test only the constrained method of an introduction abstract class is validated"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test;

import io.micronaut.aop.introduction.*;
import jakarta.validation.constraints.NotBlank;

@Stub
@jakarta.inject.Singleton
abstract class MyBean {

    public abstract String constrained(@NotBlank String name);

    public abstract String notConstrained(String name);
}
''')

        then:
        beanDefinition.getRequiredMethod("constrained", String).hasAnnotation(ANN_VALIDATED)
        !beanDefinition.getRequiredMethod("notConstrained", String).hasAnnotation(ANN_VALIDATED)
    }

    void "test a constrained method inherited from a super interface is validated"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test;

import io.micronaut.aop.introduction.*;
import jakarta.validation.constraints.NotBlank;

interface Parent {

    String constrained(@NotBlank String name);

    String notConstrained(String name);
}

@Stub
@jakarta.inject.Singleton
interface MyBean extends Parent {
}
''')

        then:
        beanDefinition.getRequiredMethod("constrained", String).hasAnnotation(ANN_VALIDATED)
        !beanDefinition.getRequiredMethod("notConstrained", String).hasAnnotation(ANN_VALIDATED)
    }

    void "test a method overriding a constrained method is validated"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test;

import io.micronaut.aop.introduction.*;
import jakarta.validation.constraints.NotBlank;

interface Parent {

    String constrained(@NotBlank String name);

    String notConstrained(String name);
}

@Stub
@jakarta.inject.Singleton
interface MyBean extends Parent {

    @Override
    String constrained(String name);

    @Override
    String notConstrained(String name);
}
''')

        then:
        beanDefinition.getRequiredMethod("constrained", String).hasAnnotation(ANN_VALIDATED)
        !beanDefinition.getRequiredMethod("notConstrained", String).hasAnnotation(ANN_VALIDATED)
    }
}
