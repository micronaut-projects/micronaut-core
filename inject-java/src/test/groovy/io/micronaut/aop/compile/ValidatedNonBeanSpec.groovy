package io.micronaut.aop.compile

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition

class ValidatedNonBeanSpec extends AbstractTypeElementSpec {

    void "test a class with only a validation annotation is not a bean"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition("test.DefaultContract", """
package test;

import javax.validation.constraints.NotNull;
import io.micronaut.context.annotation.*;
import jakarta.inject.Singleton;

class DefaultContract implements Contract {

    public Long parseLong(@NotNull CharSequence sequence) {
        return 0L;
    }
}

interface Contract {
    Long parseLong(@NotNull CharSequence sequence);
}

""")
        then:
        beanDefinition == null
    }
}
