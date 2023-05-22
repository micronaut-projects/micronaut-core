package io.micronaut.core

import io.micronaut.context.BeanContext
import spock.lang.Specification

import jakarta.inject.Provider
import jakarta.inject.Singleton

class ArgumentSpec extends Specification {

    void "test argument is provider"() {
        def context = BeanContext.run()
        def beanDefinition = context.getBeanDefinition(MyBean)

        expect:
        beanDefinition.requiredComponents.contains(String)
        beanDefinition.constructor.getArguments()[0].isProvider()

        cleanup:
        context.close()
    }

    @Singleton
    static class MyBean {

        MyBean(Provider<String> provider) {}
    }
}
