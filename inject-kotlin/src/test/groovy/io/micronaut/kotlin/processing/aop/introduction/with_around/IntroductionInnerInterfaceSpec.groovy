package io.micronaut.kotlin.processing.aop.introduction.with_around

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.InstantiatableBeanDefinition
import io.micronaut.inject.writer.BeanDefinitionVisitor
import spock.lang.Specification

import static io.micronaut.annotation.processing.test.KotlinCompiler.*

class IntroductionInnerInterfaceSpec extends Specification {

    void "test an inner class passed to @Introduction(interfaces = "() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition("test.MyBean" + BeanDefinitionVisitor.PROXY_SUFFIX, """
package test

import io.micronaut.aop.Around
import io.micronaut.aop.Introduction
import io.micronaut.context.annotation.Type
import jakarta.inject.Singleton

@Around
@Type(io.micronaut.kotlin.processing.aop.introduction.with_around.ObservableInterceptor::class)
@Introduction(interfaces = [ObservableUI.Inner::class])
@Retention
annotation class ObservableUI {

    interface Inner {
        fun hello(): String
    }
}

@Singleton
@ObservableUI
open class MyBean
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
