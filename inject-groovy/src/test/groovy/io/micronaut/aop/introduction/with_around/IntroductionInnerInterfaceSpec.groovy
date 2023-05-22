package io.micronaut.aop.introduction.with_around

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.InstantiatableBeanDefinition
import io.micronaut.inject.writer.BeanDefinitionVisitor

class IntroductionInnerInterfaceSpec extends AbstractBeanDefinitionSpec {

    void "test an inner class passed to @Introduction(interfaces = "() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition("test.MyBean" + BeanDefinitionVisitor.PROXY_SUFFIX, """
package test;

import io.micronaut.aop.introduction.*;
import io.micronaut.aop.*;
import io.micronaut.context.annotation.*;
import jakarta.inject.Singleton;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;


@Around
@Type(io.micronaut.aop.introduction.with_around.ObservableInterceptor.class)
@Introduction(interfaces = ObservableUI.Inner.class)
@Retention(RUNTIME)
@interface ObservableUI {
    public interface Inner {
        String hello();
    }
}

@Singleton
@ObservableUI
class MyBean {
}
""")

        then:
        noExceptionThrown()
        beanDefinition != null

        when:
        def context = ApplicationContext.run()
        def instance = ((InstantiatableBeanDefinition) beanDefinition).instantiate(context)

        then:
        instance.hello() == "World"
    }
}
