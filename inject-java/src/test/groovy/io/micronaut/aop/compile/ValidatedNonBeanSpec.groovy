package io.micronaut.aop.compile

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.AdvisedBeanType
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ValidatedBeanDefinition

import java.util.List

class ValidatedNonBeanSpec extends AbstractTypeElementSpec {

    void "test a class with only a validation annotation is not a bean"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition("test.DefaultContract", """
package test;

import jakarta.validation.constraints.NotNull;
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

    void "test method validation does not make the bean definition validated"() {
        when:
        BeanDefinition beanDefinition = buildInterceptedBeanDefinition("test.DefaultContract", """
package test;

import jakarta.inject.Singleton;
import jakarta.validation.constraints.NotNull;

@Singleton
class DefaultContract implements Contract {

    @Override
    public Long parseLong(@NotNull CharSequence sequence) {
        return 0L;
    }
}

interface Contract {
    Long parseLong(@NotNull CharSequence sequence);
}

""")

        then:
        beanDefinition instanceof AdvisedBeanType
        !(beanDefinition instanceof ValidatedBeanDefinition)
    }

    void "test executable methods are retained for method validation"() {
        when:
        BeanDefinition beanDefinition = buildInterceptedBeanDefinition("test.DefaultContract", """
package test;

import jakarta.inject.Singleton;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Singleton
class DefaultContract {

    public List<@NotNull String> returnStrings() {
        return null;
    }

    public void setValues(List<@NotNull String> values) {
    }
}

""")

        then:
        beanDefinition instanceof AdvisedBeanType
        !(beanDefinition instanceof ValidatedBeanDefinition)
        beanDefinition.findMethod("returnStrings").isPresent()
        beanDefinition.findMethod("setValues", List).isPresent()
    }

    void "test private executable validation bean is indexed by original type"() {
        given:
        ApplicationContext context = buildContext("test.Test\$PrivateContract", """
package test;

import jakarta.inject.Singleton;
import jakarta.validation.constraints.NotNull;
import java.util.List;

class Test {

    @Singleton
    private static class PrivateContract {

        public void setValues(List<@NotNull String> values) {
        }
    }
}

""")

        when:
        Class<?> beanType = context.classLoader.loadClass("test.Test\$PrivateContract")
        Collection<BeanDefinition<?>> beanDefinitions = context.getBeanDefinitions(beanType)

        then:
        beanDefinitions.size() == 1
        beanDefinitions.iterator().next() instanceof AdvisedBeanType
        beanDefinitions.iterator().next().findMethod("setValues", List).isPresent()

        cleanup:
        context.close()
    }
}
