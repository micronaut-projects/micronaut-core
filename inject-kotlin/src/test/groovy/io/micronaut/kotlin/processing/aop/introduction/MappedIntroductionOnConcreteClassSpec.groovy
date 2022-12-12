package io.micronaut.kotlin.processing.aop.introduction

import io.micronaut.context.ApplicationContext
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.context.event.StartupEvent
import spock.lang.Specification

import static io.micronaut.annotation.processing.test.KotlinCompiler.*

class MappedIntroductionOnConcreteClassSpec extends Specification {

    void "test mapped introduction of new interface on concrete class"() {
        given:
            ApplicationContext applicationContext = buildContext('''
package test

import jakarta.inject.Singleton

@io.micronaut.kotlin.processing.aop.introduction.ListenerAdviceMarker
@Singleton
open class MyBeanWithMappedIntroduction
''')
        applicationContext.registerSingleton(new ListenerAdviceInterceptor())

        when:
        def beanClass = applicationContext.classLoader.loadClass('test.MyBeanWithMappedIntroduction')
        def cc = applicationContext.getBean(beanClass)
        def listenerAdviceInterceptor = applicationContext.getBean(ListenerAdviceInterceptor)

        then:
        cc instanceof ApplicationEventListener

        when:
        def event = new StartupEvent(applicationContext)
        cc.onApplicationEvent(event)

        then:
        listenerAdviceInterceptor.recievedMessages.contains(event)

        cleanup:
        listenerAdviceInterceptor.recievedMessages.clear()
    }
}
