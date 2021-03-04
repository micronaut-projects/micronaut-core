package io.micronaut.aop.compile

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanContext
import io.micronaut.inject.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanFactory
import io.micronaut.inject.writer.BeanDefinitionVisitor

class IntroductionWithAroundSpec extends AbstractTypeElementSpec {

    void "test that around advice is applied to introduction concrete methods"() {
        when:"An introduction advice type is compiled that includes a concrete method that is annotated with around advice"
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test;

import io.micronaut.aop.introduction.*;
import io.micronaut.context.annotation.*;
import java.net.*;
import javax.validation.constraints.*;
import javax.inject.Singleton;

@Stub
@Singleton
abstract class MyBean {
    abstract void save(@NotBlank String name, @Min(1L) int age);
    abstract void saveTwo(@Min(1L) String name);
    
    @io.micronaut.aop.simple.Mutating("name")
    public String myConcrete(String name) {
        return name;
    }
}

''')

        then:"The around advice is applied to the concrete method"
        beanDefinition != null

        when:
        ApplicationContext context = ApplicationContext.run()
        def instance = ((BeanFactory) beanDefinition).build(context, beanDefinition)

        then:
        instance.myConcrete("test") == 'changed'

        cleanup:
        context.close()
    }
}
